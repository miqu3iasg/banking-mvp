package com.miqu3iasg.banking.pix.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.miqu3iasg.banking.pix.metrics.PixMetrics;
import com.miqu3iasg.banking.pix.exception.PixGatewayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * Gateway for managing EVP (Endereço Virtual de Pagamento) PIX keys at Efí Bank.
 *
 * <p><strong>What is EVP:</strong> EVP stands for <em>Endereço Virtual de Pagamento</em> — a randomly generated UUID
 * issued by BACEN's DICT (Diretório de Identificadores de Contas Transacionais) that
 * serves as a PIX key. Unlike CPF, CNPJ, phone, or e-mail keys, EVP keys have no
 * personal meaning: they are opaque UUIDs that route payments to a specific account.
 *
 * <p><strong>When to use EVP vs regular PIX keys:</strong>
 * <ul>
 *   <li><strong>EVP</strong> — preferred for businesses that need multiple dynamic keys
 *       (e.g. one per product, merchant, or payment link), or when privacy is required
 *       and the payer should not see the recipient's CPF/CNPJ/phone/e-mail.</li>
 *   <li><strong>Regular keys</strong> (CPF, CNPJ, phone, e-mail) — suitable for
 *       individuals or companies that want a single, memorable key tied to an identity.
 *       Most end-users already have these registered.</li>
 * </ul>
 *
 * <p><strong>Limits:</strong> Efí Bank enforces a maximum number of active EVP keys per account. Attempting to
 * exceed this limit returns HTTP 400 and is surfaced as a {@link PixGatewayException}.
 *
 * <p><strong>⚠ PRODUCTION-ONLY — SANDBOX NOT SUPPORTED:</strong><br>
 * All three EVP endpoints ({@code POST}, {@code GET}, and {@code DELETE /v2/gn/evp}) are
 * <em>unavailable in Efí Bank's homologation (sandbox) environment</em>. Calls made against
 * the sandbox base URL ({@code pix-h.api.efipay.com.br}) will return HTTP 500 with
 * {@code "erro_aplicacao"} or {@code "erro_interno_servidor"}, regardless of credentials or
 * request correctness. This is a documented provider limitation, not a bug in this gateway.
 *
 * <p>Consequences for testing and CI:
 * <ul>
 *   <li>E2E tests covering this gateway cannot run against the sandbox. They must either
 *       target production credentials or be stubbed with WireMock.</li>
 *   <li>Do not attempt to validate EVP behaviour in any pipeline that uses
 *       {@code efi.pix.sandbox=true}.</li>
 * </ul>
 *
 * <p>Reference: <a href="https://dev.efipay.com.br/docs/api-pix/chaves-pix">Efí Bank — Chaves PIX (EVP)</a>
 */
@Slf4j
@Component
public class EfiEvpGateway {

	private final WebClient webClient;
	private final EfiPixAuthGateway authGateway;
	private final PixMetrics metrics;

	public EfiEvpGateway (
		@Qualifier("efiPixWebClient") WebClient webClient,
		EfiPixAuthGateway authGateway,
		PixMetrics metrics
	) {
		this.webClient = webClient;
		this.authGateway = authGateway;
		this.metrics = metrics;
	}

	/**
	 * Creates a new EVP (random) PIX key via Efí Bank {@code POST /v2/gn/evp}.
	 *
	 * <p>Each call generates a fresh UUID from BACEN DICT and registers it
	 * against the authenticated account. The returned key can immediately be
	 * used as a PIX key in charge creation.
	 *
	 * <p><strong>⚠ Production only.</strong> This endpoint is not available in the Efí Bank
	 * sandbox environment. Sandbox calls will fail with HTTP 500 ({@code "erro_aplicacao"}).
	 * See the class-level Javadoc for full details.
	 *
	 * @return the newly created EVP key UUID
	 * @throws PixGatewayException if the account has reached its EVP key limit (HTTP 400),
	 *                             or if any other provider error occurs
	 */
	public String createEvpKey () {
		log.info("Calling Efí Bank POST /v2/gn/evp (create random PIX key)");

		return metrics.timeGatewayCall("createEvpKey", () -> {
			var token = authGateway.getAccessToken();
			var response = webClient.post()
				.uri("/v2/gn/evp")
				.header("Authorization", "Bearer " + token)
				.retrieve()
				.onStatus(status -> status == HttpStatus.BAD_REQUEST, clientResponse ->
					clientResponse
						.bodyToMono(String.class)
						.map(body -> new PixGatewayException("EVP key creation failed; limit reached: " + body))
				)
				.onStatus(HttpStatusCode::isError, clientResponse ->
					clientResponse
						.bodyToMono(String.class)
						.map(body -> new PixGatewayException("Efí Bank EVP create error: " + body))
				)
				.bodyToMono(EvpCreateResponse.class)
				.block(Duration.ofSeconds(10));

			if (response == null || response.key() == null) {
				throw new PixGatewayException("Efí Bank returned empty EVP key response");
			}

			log.info("Efí Bank EVP key created: key={}", response.key());

			metrics.recordEvpKeyCreated();

			return response.key();
		});
	}

	/**
	 * Lists all active EVP keys registered to the authenticated account via Efí Bank {@code GET /v2/gn/evp}.
	 *
	 * <p>Returns an empty list if the account has no active EVP keys, rather than throwing.
	 *
	 * <p><strong>⚠ Production only.</strong> This endpoint is not available in the Efí Bank
	 * sandbox environment. Sandbox calls will fail with HTTP 500 ({@code "erro_interno_servidor"}).
	 * See the class-level Javadoc for full details.
	 *
	 * @return an immutable list of EVP key UUIDs; never {@code null}, may be empty
	 * @throws PixGatewayException if the provider returns an unexpected error
	 */
	public List<String> listEvpKeys () {
		log.debug("Calling Efí Bank GET /v2/gn/evp (list random PIX keys)");

		return metrics.timeGatewayCall("listEvpKeys", () -> {
			var token = authGateway.getAccessToken();
			var response = webClient.get()
				.uri("/v2/gn/evp")
				.header("Authorization", "Bearer " + token)
				.retrieve()
				.onStatus(HttpStatusCode::isError, clientResponse ->
					clientResponse
						.bodyToMono(String.class)
						.map(body -> new PixGatewayException("Efí Bank EVP list error: " + body))
				)
				.bodyToMono(EvpListResponse.class)
				.block(Duration.ofSeconds(10));

			if (response == null || response.keys() == null) {
				return List.of();
			}

			metrics.recordEvpKeyListed();

			return response.keys();
		});
	}

	/**
	 * Deletes a random (EVP) PIX key from Efí Bank via {@code DELETE /v2/gn/evp/{key}}.
	 *
	 * <p><strong>WARNING — IRREVERSIBLE:</strong> The UUID generated by BACEN DICT
	 * cannot be recreated after deletion. Any pending charges linked to this key
	 * will become unpayable. Only call this after explicit user confirmation.
	 *
	 * <p><strong>⚠ Production only.</strong> This endpoint is not available in the Efí Bank
	 * sandbox environment. See the class-level Javadoc for full details.
	 *
	 * @param key the UUID of the EVP key to delete
	 * @throws PixGatewayException if deletion fails with an unexpected provider error
	 */
	public void deleteEvpKey (String key) {
		log.info("Calling Efí Bank DELETE /v2/gn/evp/{} (delete random PIX key)", key);

		metrics.timeGatewayCall("deleteEvpKey", () -> {
			var token = authGateway.getAccessToken();

			webClient.delete()
				.uri("/v2/gn/evp/{key}", key)
				.header("Authorization", "Bearer " + token)
				.retrieve()
				.onStatus(
					status -> status != HttpStatus.BAD_REQUEST && status.isError(), clientResponse ->
						clientResponse
							.bodyToMono(String.class)
							.map(body -> new PixGatewayException("Efí Bank EVP delete error: " + body))
				)
				.bodyToMono(Void.class)
				.block(Duration.ofSeconds(10));

			log.info("Efí Bank EVP key deleted: key={}", key);
			metrics.recordEvpKeyDeleted();

			return null;
		});
	}

	/**
	 * Response for POST /v2/gn/evp: { "key": "uuid" }
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record EvpCreateResponse(@JsonProperty("chave") String key) { }

	/**
	 * Response for GET /v2/gn/evp: { "keys": ["uuid1", "uuid2"] }
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record EvpListResponse(@JsonProperty("chaves") List<String> keys) { }
}
