package com.miqu3iasg.banking_mvp.efi.pix.gateway;

import com.miqu3iasg.banking.pix.exception.PixGatewayException;
import com.miqu3iasg.banking.pix.gateway.PixChargeCreationResponse;
import com.miqu3iasg.banking.pix.gateway.PixChargeRequest;
import com.miqu3iasg.banking.pix.gateway.PixChargeResponse;
import com.miqu3iasg.banking_mvp.transaction.service.AbstractE2ETestSupport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

@Slf4j
public class EfiPixGatewayE2ETest extends AbstractE2ETestSupport {
	private static final String ACTIVE_STATUS = "ATIVA";
	private static final String REMOVED_STATUS = "REMOVIDA_PELO_USUARIO_RECEBEDOR";

	private String txid;

	@Test
	@DisplayName("createCharge() should return txid, location and EMV copy-paste when creating a charge via gateway")
	void shouldReturnTxidLocationAndEmvCopyPasteWhenCreatingChargeViaGateway () {
		txid = generateTxid();

		PixChargeCreationResponse response = efiPixGateway.createCharge(buildRequest(txid));

		assertThat(response.txid())
			.isEqualTo(txid)
			.hasSize(BacenSpec.TXID_LENGTH);

		assertThat(response.location())
			.isNotBlank()
			.satisfies(loc ->
				assertThat(loc)
					.containsAnyOf(
						BacenSpec.EFI_LOCATION_DOMAIN_PROD,
						BacenSpec.EFI_LOCATION_DOMAIN_SANDBOX
					)
			);

		assertThat(response.copyPaste())
			.isNotBlank()
			.startsWith(BacenSpec.EMV_QR_CODE_PREFIX)
			.hasSizeGreaterThan(50)
			.doesNotContainAnyWhitespaces();
	}

	@Test
	@DisplayName("PUT /v2/cob/{txid} should return 201 with status ATIVA when creating a charge")
	void shouldReturn201WithStatusATIVAWhenCreateChargeViaRawHttp () throws IOException {
		txid = generateTxid();
		String body = buildEfiChargeBody();

		byte[] responseBody = sandboxClient()
			.put()
			.uri("/v2/cob/{txid}", txid)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(body)
			.exchange()
			.expectStatus().isCreated()
			.expectBody()
			.jsonPath("$.txid").isEqualTo(txid)
			.jsonPath("$.status").isEqualTo(ACTIVE_STATUS)
			.jsonPath("$.pixCopiaECola").isNotEmpty()
			.returnResult()
			.getResponseBody();

		PixChargeCreationResponse charge = objectMapper.readValue(responseBody, PixChargeCreationResponse.class);

		assertThat(charge.txid()).isEqualTo(txid);
		assertThat(charge.copyPaste()).isNotBlank();
	}

	@Test
	@DisplayName("PUT /v2/cob/{txid} should return 401 from sandbox when bearer token is invalid")
	void shouldReturn401FromSandboxWhenBearerTokenIsInvalid () {
		WebTestClient unauthenticated = WebTestClient
			.bindToWebHandler(exchange -> {
				URI requestUri = exchange.getRequest().getURI();
				String pathAndQuery = requestUri.getRawPath() + (requestUri.getRawQuery() != null ? "?" + requestUri.getRawQuery() : "");

				return efiPixWebClient
					.mutate()
					.defaultHeader("Authorization", "Bearer invalid_token")
					.defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
					.build()
					.method(exchange.getRequest().getMethod())
					.uri(pathAndQuery)
					.headers(h -> h.addAll(exchange.getRequest().getHeaders()))
					.body(exchange.getRequest().getBody(), DataBuffer.class)
					.exchangeToMono(response -> {
						exchange.getResponse().setStatusCode(response.statusCode());
						exchange.getResponse().getHeaders().addAll(response.headers().asHttpHeaders());
						return response.bodyToMono(DataBuffer.class)
							.flatMap(body -> exchange.getResponse().writeWith(Mono.just(body)))
							.switchIfEmpty(exchange.getResponse().setComplete());
					});
			})
			.build();

		txid = generateTxid();

		unauthenticated.put().uri("/v2/cob/{txid}", txid)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(buildEfiChargeBody())
			.exchange()
			.expectStatus().isUnauthorized();
	}

	@Test
	@DisplayName("cancelCharge() should not throw and sandbox should reflect removal when cancelling an active charge")
	void shouldCancelActiveChargeWithoutThrowingAndSandboxShouldReflectRemoval () {
		txid = generateTxid();

		// values above R$10.00 aren't confirmed automatically by Efí Bank API
		var request = new PixChargeRequest(txid, new BigDecimal("11.00"), "E2E Payer", CPF_1, PIX_KEY, 3600);

		PixChargeCreationResponse created = efiPixGateway.createCharge(request);
		assertThat(created.txid()).isEqualTo(txid);

		assertThatCode(() -> efiPixGateway.cancelCharge(txid))
			.doesNotThrowAnyException();

		await()
			.atMost(5, TimeUnit.SECONDS)
			.pollInterval(500, TimeUnit.MILLISECONDS)
			.untilAsserted(() ->
				sandboxClient()
					.get()
					.uri("/v2/cob/{txid}", txid)
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.txid").isEqualTo(txid)
					.jsonPath("$.status").isEqualTo(REMOVED_STATUS)
			);
	}

	@Test
	@DisplayName("getCharge() should return present Optional with correct txid when charge exists")
	void shouldReturnPresentOptionalWithCorrectTxidWhenChargeExists () {
		txid = generateTxid();

		PixChargeCreationResponse created = efiPixGateway.createCharge(buildRequest(txid));
		assertThat(created.txid()).isEqualTo(txid);

		Optional<PixChargeResponse> retrieved = efiPixGateway.getCharge(txid);

		assertThat(retrieved)
			.isPresent()
			.hasValueSatisfying(charge -> {
				assertThat(charge.txid()).isEqualTo(txid);
				assertThat(charge.status()).isIn(ACTIVE_STATUS, REMOVED_STATUS);
				assertThat(charge.copyPaste()).isNotBlank().startsWith(BacenSpec.EMV_QR_CODE_PREFIX);
			});
	}

	@Test
	@DisplayName("createCharge() should evict stale OAuth token, re-authenticate and succeed after receiving 401")
	void shouldEvictOauthTokenCacheAndRetryAfterReceiving401OnChargeCreation () {
		Cache tokenCache = cacheManager.getCache("efi-oauth-token");
		assertThat(tokenCache).isNotNull();

		tokenCache.put("access_token", "deliberately-invalid-token");

		txid = generateTxid();
		PixChargeCreationResponse response = efiPixGateway.createCharge(buildRequest(txid));

		assertThat(response).isNotNull();

		assertThat(response.txid())
			.isEqualTo(txid)
			.hasSize(BacenSpec.TXID_LENGTH);

		assertThat(response.location())
			.isNotBlank()
			.satisfies(loc ->
				assertThat(loc)
					.containsAnyOf(
						BacenSpec.EFI_LOCATION_DOMAIN_PROD,
						BacenSpec.EFI_LOCATION_DOMAIN_SANDBOX
					)
			);

		assertThat(response.copyPaste())
			.isNotBlank()
			.startsWith(BacenSpec.EMV_QR_CODE_PREFIX)
			.hasSizeGreaterThan(50)
			.doesNotContainAnyWhitespaces();

		Cache.ValueWrapper refreshedToken = tokenCache.get("access_token");
		assertThat(refreshedToken).isNotNull();

		String newToken = (String) refreshedToken.get();
		assertThat(newToken)
			.isNotBlank()
			.isNotEqualTo("deliberately-invalid-token")
			.hasSizeGreaterThan(20)
			.doesNotContainAnyWhitespaces();
	}

	@AfterEach
	void cleanup () {
		if (txid != null) {
			try {
				efiPixGateway.cancelCharge(txid);
			} catch (PixGatewayException e) {
				// charge may already be cancelled by the test itself — this is expected
				log.debug("cleanup: cancelCharge skipped for txid={} ({})", txid, e.getMessage());
			} finally {
				txid = null;
			}
		}
	}

	private static PixChargeRequest buildRequest (String txid) {
		return new PixChargeRequest(txid, new BigDecimal("0.01"), "E2E Payer", CPF_1, PIX_KEY, 3600);
	}

	private String buildEfiChargeBody () {
		return """
			{
			  "calendario": { "expiracao": 3600 },
			  "devedor": { "cpf": "52998224725", "nome": "E2E Payer" },
			  "valor": { "original": "0.01" },
			  "chave": "%s",
			  "solicitacaoPagador": "E2E test"
			}
			""".formatted(PIX_KEY);
	}
}
