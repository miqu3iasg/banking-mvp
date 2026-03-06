package com.miqu3iasg.banking_mvp.efi.pix.gateway;

import com.miqu3iasg.banking.pix.exception.PixGatewayException;
import com.miqu3iasg.banking.pix.gateway.PixChargeCreationResponse;
import com.miqu3iasg.banking.pix.gateway.PixChargeRequest;
import com.miqu3iasg.banking.pix.gateway.PixChargeResponse;
import com.miqu3iasg.banking_mvp.shared.support.AbstractE2eTestSupport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

@Slf4j
class EfiPixGatewayE2eTest extends AbstractE2eTestSupport {

	private static final String STATUS_ACTIVE = "ATIVA";
	private static final String STATUS_CONFIRMED = "CONCLUIDA";
	private static final String STATUS_REMOVED = "REMOVIDA_PELO_USUARIO_RECEBEDOR";

	// Efí Sandbox auto-confirms charges at or below R$10.00.
	private static final BigDecimal AMOUNT_AUTO_CONFIRMED = new BigDecimal("0.01");
	private static final BigDecimal AMOUNT_STAYS_ACTIVE = new BigDecimal("11.00");

	private String activeTxid;

	@Test
	@DisplayName("The returned txid must match the submitted one and conform to the BACEN-specified length.")
	void createChargeReturnsEchoedTxidWithCorrectLength () {
		activeTxid = generateTxid();

		PixChargeCreationResponse response = efiPixGateway
			.createCharge(chargeRequest(activeTxid, AMOUNT_AUTO_CONFIRMED));

		assertThat(response.txid())
			.isNotBlank()
			.isEqualTo(activeTxid)
			.hasSize(BacenSpec.TXID_LENGTH);
	}

	@Test
	@DisplayName("The copy-paste field must be a structurally valid EMV QR code with the expected prefix and minimum length.")
	void createChargeReturnsCopyPasteThatIsStructurallyValidEmvQrCode () {
		activeTxid = generateTxid();

		PixChargeCreationResponse response = efiPixGateway
			.createCharge(chargeRequest(activeTxid, AMOUNT_AUTO_CONFIRMED));

		assertThat(response.copyPaste())
			.isNotBlank()
			.startsWith(BacenSpec.EMV_QR_CODE_PREFIX)
			.doesNotContainAnyWhitespaces()
			.hasSizeGreaterThan(100);
	}

	@Test
	@DisplayName("Sandbox must return 201 with the full Efí charge payload; acting as a schema regression guard against provider drift.")
	void createChargeRawHttpSandboxReturns201WithFullChargePayload () {
		activeTxid = generateTxid();

		sandboxClient()
			.put()
			.uri("/v2/cob/{txid}", activeTxid)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(efiChargeJson(AMOUNT_AUTO_CONFIRMED))
			.exchange()
			.expectStatus().isCreated()
			.expectBody()
			.jsonPath("$.txid").isEqualTo(activeTxid)
			.jsonPath("$.status").isEqualTo(STATUS_ACTIVE)
			.jsonPath("$.calendario.expiracao").isEqualTo(3600)
			.jsonPath("$.valor.original").isEqualTo("0.01")
			.jsonPath("$.chave").isEqualTo(PIX_KEY)
			.jsonPath("$.pixCopiaECola").isNotEmpty()
			.jsonPath("$.loc.location").isNotEmpty()
			.jsonPath("$.revisao").isEqualTo(0);
	}

	@Test
	@DisplayName("Sandbox must enforce authentication by returning 401 when an invalid bearer token is provided.")
	void createChargeRawHttpSandboxReturns401WhenBearerTokenIsInvalid () {
		unauthenticatedBearerSandboxClient()
			.put()
			.uri("/v2/cob/{txid}", generateTxid())
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(efiChargeJson(AMOUNT_AUTO_CONFIRMED))
			.exchange()
			.expectStatus().isUnauthorized();
	}

	@Test
	@DisplayName("Fetching an existing charge must return a present Optional containing the correct txid, a valid status, and a non-blank copy-paste.")
	void getChargeReturnsPresentOptionalWithEmbeddedTxidInCopyPasteForExistingCharge () {
		activeTxid = generateTxid();
		efiPixGateway.createCharge(chargeRequest(activeTxid, AMOUNT_AUTO_CONFIRMED));

		Optional<PixChargeResponse> result = efiPixGateway.getCharge(activeTxid);
		assertThat(result).isPresent();

		PixChargeResponse charge = result.get();

		assertThat(charge.txid()).isEqualTo(activeTxid);
		assertThat(charge.status()).isIn(STATUS_ACTIVE, STATUS_CONFIRMED);
		assertThat(charge.copyPaste())
			.isNotBlank()
			.startsWith(BacenSpec.EMV_QR_CODE_PREFIX);
	}

	@Test
	@DisplayName("Fetching a non-existent charge must return an empty Optional instead of throwing; the gateway absorbs provider 404s as a normal condition.")
	void getChargeReturnsEmptyOptionalInsteadOfThrowingWhenChargeDoesNotExist () {
		// The gateway contract requires absorbing provider 404s as an empty Optional.
		// Callers poll getCharge() without knowing whether creation succeeded;
		// throwing here would force every caller to catch PixGatewayException for a normal condition.
		Optional<PixChargeResponse> result = efiPixGateway.getCharge(generateTxid());

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("Cancelling an active charge must complete without throwing and the sandbox must confirm the REMOVIDA status.")
	void cancelChargeDoesNotThrowAndSandboxConfirmsRemovalForActiveCharge () {
		activeTxid = generateTxid();
		efiPixGateway.createCharge(chargeRequest(activeTxid, AMOUNT_STAYS_ACTIVE));

		assertThatCode(() -> efiPixGateway.cancelCharge(activeTxid))
			.doesNotThrowAnyException();

		await()
			.atMost(5, TimeUnit.SECONDS)
			.pollInterval(500, TimeUnit.MILLISECONDS)
			.untilAsserted(() ->
				sandboxClient()
					.get()
					.uri("/v2/cob/{txid}", activeTxid)
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.status").isEqualTo(STATUS_REMOVED)
			);
	}

	@Test
	@DisplayName("Cancelling an already-cancelled charge must not throw; the gateway absorbs the Efí 400 and treats the operation as idempotent.")
	void cancelChargeIsIdempotentDoesNotThrowOnDoubleCancel () {
		activeTxid = generateTxid();

		efiPixGateway.createCharge(chargeRequest(activeTxid, AMOUNT_STAYS_ACTIVE));

		efiPixGateway.cancelCharge(activeTxid);

		assertThatCode(() -> efiPixGateway.cancelCharge(activeTxid))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Cancelling a non-existent charge must not throw; the gateway absorbs the Efí 404 gracefully.")
	void cancelChargeDoesNotThrowForNonExistentTxid () {
		assertThatCode(() -> efiPixGateway.cancelCharge(generateTxid()))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("The first call to obtain an access token must return a valid opaque non-blank string with no whitespace.")
	void getAccessTokenReturnsValidOpaqueTokenOnFirstCall () {
		String token = authGateway.getAccessToken();

		assertThat(token)
			.isNotBlank()
			.doesNotContainAnyWhitespaces()
			.hasSizeGreaterThan(20);
	}

	@Test
	@DisplayName("Subsequent calls within the TTL window must return the same cached token without triggering a new network round-trip.")
	void getAccessTokenReturnsSameTokenOnSubsequentCallWithinTtl () {
		String first = authGateway.getAccessToken();
		String second = authGateway.getAccessToken();

		assertThat(second).isEqualTo(first);
	}

	@Test
	@DisplayName("Verifies that a stale cached token is evicted and the request transparently recovers after receiving a 401 response from Efí.")
	void createChargeEvictsStaleCachedTokenAndRecoversTransparentlyAfter401 () {
		Cache tokenCache = cacheManager.getCache("efi-oauth-token");
		assertThat(tokenCache).isNotNull();

		tokenCache.put("access_token", "deliberately-invalid-token");

		activeTxid = generateTxid();

		PixChargeCreationResponse response = efiPixGateway
			.createCharge(chargeRequest(activeTxid, AMOUNT_AUTO_CONFIRMED));

		assertThat(response).isNotNull();

		assertThat(response.txid())
			.isEqualTo(activeTxid)
			.hasSize(BacenSpec.TXID_LENGTH);

		assertThat(response.location())
			.isNotBlank()
			.containsAnyOf(
				BacenSpec.EFI_LOCATION_DOMAIN_PROD,
				BacenSpec.EFI_LOCATION_DOMAIN_SANDBOX);

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
	void cancelActiveCharge () {
		if (activeTxid == null) return;
		try {
			efiPixGateway.cancelCharge(activeTxid);
		} catch (PixGatewayException e) {
			log.warn("Post-test cleanup failed for txid={}; sandbox state may be dirty: {}", activeTxid, e.getMessage());
		} finally {
			activeTxid = null;
		}
	}

	private static PixChargeRequest chargeRequest (String txid, BigDecimal amount) {
		return new PixChargeRequest(txid, amount, "E2E Payer", CPF_1, PIX_KEY, 3600);
	}

	/**
	 * Raw Efí JSON body for direct sandbox HTTP assertions.
	 * Must match Efí's schema exactly (not our internal DTOs) because these tests
	 * exist precisely to catch drift between the two.
	 */
	private String efiChargeJson (BigDecimal amount) {
		return """
			{
			  "calendario": { "expiracao": 3600 },
			  "devedor": { "cpf": "%s", "nome": "E2E Payer" },
			  "valor": { "original": "%s" },
			  "chave": "%s",
			  "solicitacaoPagador": "E2E test"
			}
			""".formatted(CPF_1, String.format(Locale.US, "%.2f", amount), PIX_KEY);
	}
}
