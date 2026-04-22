package com.miqu3iasg.banking.pix.gateway;

import com.miqu3iasg.banking.pix.exception.PixAuthenticationException;
import com.miqu3iasg.banking.pix.gateway.dto.EfiCreateChargeRequest;
import com.miqu3iasg.banking.pix.gateway.dto.EfiCreateChargeResponse;
import com.miqu3iasg.banking.pix.gateway.dto.EfiGetChargeResponse;
import com.miqu3iasg.banking.pix.metrics.PixMetrics;
import com.miqu3iasg.banking.shared.config.RetryProperties;
import com.miqu3iasg.banking.shared.domain.Money;
import com.miqu3iasg.banking.pix.exception.PixGatewayException;
import com.miqu3iasg.banking.pix.exception.ProviderChargeNotFoundException;
import com.miqu3iasg.banking.shared.exception.TransientExceptionClassifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ConditionalOnProperty(name = "efi.webclient.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class EfiPixGateway implements PixGateway {
	private static final int CNPJ_LENGTH = 14;
	private static final String EFI_REMOVED_STATUS = "REMOVIDA_PELO_USUARIO_RECEBEDOR";

	private final WebClient webClient;
	private final EfiPixAuthGateway authGateway;
	private final PixMetrics metrics;
	private final CacheManager cacheManager;
	private final RetryProperties props;
	private final RetryTemplate retryTemplate;

	public EfiPixGateway (
		@Qualifier("efiPixWebClient") WebClient webClient,
		EfiPixAuthGateway authGateway,
		PixMetrics metrics,
		CacheManager cacheManager,
		RetryProperties props,
		@Qualifier("efiPixRetryTemplate") RetryTemplate retryTemplate
	) {
		this.webClient = webClient;
		this.authGateway = authGateway;
		this.metrics = metrics;
		this.cacheManager = cacheManager;
		this.props = props;
		this.retryTemplate = retryTemplate;
		this.retryTemplate.registerListener(efiRetryListener());
	}

	@Override
	public PixChargeCreationResponse createCharge (PixChargeRequest request) {
		log.info("Calling Efí Bank PUT /v2/cob/{} amount={}", request.txid(), request.amount());

		return metrics.timeGatewayCall("createCharge", () ->
			retryTemplate.execute(context -> {
				context.setAttribute("operation", "createCharge");
				context.setAttribute("txid", request.txid());

				var token = authGateway.getAccessToken();
				var body = toEfiCreateRequest(request);

				var response = webClient.put()
					.uri("/v2/cob/{txid}", request.txid())
					.header("Authorization", "Bearer " + token)
					.bodyValue(body)
					.retrieve()
					.onStatus(HttpStatusCode::isError, clientResponse ->
						clientResponse
							.bodyToMono(String.class)
							.flatMap(errorBody -> {
								if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED) {
									evictOAuthTokenCache();

									return Mono.error(new PixAuthenticationException(
											"Efí Bank 401 on createCharge; token evicted, will retry: " + errorBody
										)
									);
								}

								log.error("Efí Bank createCharge error: status={} body={} txid={}",
									clientResponse.statusCode(),
									errorBody,
									request.txid());

								return Mono.error(new PixGatewayException(
										"Efí Bank returned %s for createCharge: %s"
											.formatted(clientResponse.statusCode(), errorBody)
									)
								);
							})
					)
					.bodyToMono(EfiCreateChargeResponse.class)
					.block(Duration.ofSeconds(10));

				if (response == null) {
					throw new PixGatewayException(
						"Efí Bank returned empty body for createCharge txid=%s"
							.formatted(request.txid())
					);
				}

				log.info("Efí Bank createCharge success: txid={} status={} revision={}",
					response.txid(),
					response.status(),
					response.revision());

				metrics.recordChargeCreated();

				return new PixChargeCreationResponse(
					response.txid(),
					response.resolveLocation(),
					response.copyPaste()
				);
			})
		);
	}

	@Override
	public Optional<PixChargeResponse> getCharge (String txid) {
		log.debug("Calling Efí Bank GET /v2/cob/{}", txid);

		return metrics.timeGatewayCall("getCharge", () ->
			retryTemplate.execute(context -> {
				context.setAttribute("operation", "getCharge");
				context.setAttribute("txid", txid);

				var token = authGateway.getAccessToken();

				var response = webClient.get()
					.uri("/v2/cob/{txid}", txid)
					.header("Authorization", "Bearer " + token)
					.retrieve()
					.onStatus(
						status -> status == HttpStatus.NOT_FOUND,
						clientResponse -> clientResponse.releaseBody().then(Mono.empty())
					)
					.onStatus(
						status -> status == HttpStatus.BAD_REQUEST,
						clientResponse -> clientResponse
							.bodyToMono(String.class)
							.flatMap(errorBody ->
								errorBody.contains("cobranca_nao_encontrada")
									? Mono.empty()
									: Mono.error(
									new PixGatewayException(
										"Efí Bank getCharge error %s: %s"
											.formatted(clientResponse.statusCode(), errorBody)
									)
								)
							)
					)
					.onStatus(HttpStatusCode::isError, clientResponse ->
						clientResponse
							.bodyToMono(String.class)
							.flatMap(errorBody -> {
								if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED) {
									evictOAuthTokenCache();

									return Mono.error(new PixAuthenticationException(
											"Efí Bank 401 on getCharge; token evicted, will retry: " + errorBody
										)
									);
								}

								return Mono.error(new PixGatewayException(
										"Efí Bank getCharge error %s: %s"
											.formatted(clientResponse.statusCode(), errorBody)
									)
								);
							})
					)
					.bodyToMono(EfiGetChargeResponse.class)
					.onErrorResume(ProviderChargeNotFoundException.class, e -> Mono.empty())
					.blockOptional(Duration.ofSeconds(10));

				return response.map(r -> new PixChargeResponse(r.txid(), r.status(), r.copyPaste()));
			})
		);
	}

	@Override
	public void cancelCharge (String txid) {
		log.info("Calling Efí Bank PATCH /v2/cob/{} (cancel)", txid);

		metrics.timeGatewayCall("cancelCharge", () ->
			retryTemplate.execute(context -> {
				context.setAttribute("operation", "cancelCharge");
				context.setAttribute("txid", txid);

				String token = authGateway.getAccessToken();

				Map<String, String> cancelBody = Map.of("status", EFI_REMOVED_STATUS);

				webClient.patch()
					.uri("/v2/cob/{txid}", txid)
					.header("Authorization", "Bearer " + token)
					.bodyValue(cancelBody)
					.retrieve()
					.onStatus(
						status -> status == HttpStatus.BAD_REQUEST || status == HttpStatus.NOT_FOUND,
						clientResponse -> clientResponse.bodyToMono(String.class).then(Mono.empty())
					)
					.onStatus(HttpStatusCode::isError,
						clientResponse ->
							clientResponse
								.bodyToMono(String.class)
								.flatMap(errorBody -> {
									if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED) {
										evictOAuthTokenCache();

										return Mono.error(new PixAuthenticationException(
												"Efí Bank 401 on cancelCharge; token evicted, will retry: " + errorBody
											)
										);
									}

									return Mono.error(new PixGatewayException(
											"Efí Bank cancelCharge error %s: %s"
												.formatted(clientResponse.statusCode(), errorBody)
										)
									);
								})
					)
					.bodyToMono(Void.class)
					.block(Duration.ofSeconds(10));

				log.info("Efí Bank cancelCharge success txid={}", txid);

				return null;
			})
		);
	}

	private RetryListener efiRetryListener () {
		return new RetryListener() {
			@Override
			public <T, E extends Throwable> void onError (
				RetryContext context, RetryCallback<T, E> callback, Throwable t) {
				int attempt = context.getRetryCount(); // 0-indexed; 0 = first failure
				String operation = (String) context.getAttribute("operation");
				String txid = (String) context.getAttribute("txid");

				if (TransientExceptionClassifier.isRetryable(t)) {
					log.warn("Efí Bank transient error on {} txid={} (attempt {}/{}), retrying…",
						operation,
						txid,
						attempt + 1,
						props.maxAttempts(),
						t);

					metrics.recordGatewayRetry(operation != null ? operation : "-", attempt + 1);
				} else {
					log.error("Non-retryable failure on Efí Bank {} txid={} (attempt {})",
						operation,
						txid,
						attempt + 1, t);
				}

				log.error("Efí Bank {} attempt {} failed for txid={}: {}",
					operation,
					attempt + 1,
					txid,
					t.getMessage());
			}
		};
	}

	private EfiCreateChargeRequest toEfiCreateRequest (PixChargeRequest req) {
		EfiCreateChargeRequest.Payer payer = null;

		if (req.payerCpfCnpj() != null && req.payerName() != null) {
			payer = req.payerCpfCnpj().length() == CNPJ_LENGTH
				? EfiCreateChargeRequest.Payer.cnpj(req.payerCpfCnpj(), req.payerName())
				: EfiCreateChargeRequest.Payer.cpf(req.payerCpfCnpj(), req.payerName());
		}

		return new EfiCreateChargeRequest(
			new EfiCreateChargeRequest.Schedule(req.expiresInSeconds()),
			payer,
			EfiCreateChargeRequest.Amount.of(Money.brl(req.amount())),
			req.pixKey(),
			null
		);
	}

	private void evictOAuthTokenCache () {
		var cache = cacheManager.getCache("efi-oauth-token");

		if (cache != null) {
			cache.evict("access_token");
			log.info("Evicted Efí Bank OAuth2 token from cache after 401 response");
		}
	}
}
