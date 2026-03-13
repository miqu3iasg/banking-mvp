package com.miqu3iasg.banking.boleto.gateway;

import com.miqu3iasg.banking.boleto.config.EfiBoletoProperties;
import com.miqu3iasg.banking.boleto.exception.BoletoGatewayException;
import com.miqu3iasg.banking.boleto.gateway.dto.EfiGetChargeDetailResponse;
import com.miqu3iasg.banking.boleto.gateway.dto.EfiIssueBoletoRequest;
import com.miqu3iasg.banking.boleto.gateway.dto.EfiIssueBoletoResponse;
import com.miqu3iasg.banking.boleto.metrics.BoletoMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
// TODO: add retry with exponencial backoff
public class EfiBoletoGateway implements BoletoGateway {

	private static final int CNPJ_LENGTH = 14;
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final WebClient webClient;
	private final EfiBoletoAuthGateway authGateway;
	private final EfiBoletoProperties props;
	private final BoletoMetrics metrics;
	private final CacheManager cacheManager;

	public EfiBoletoGateway (
		@Qualifier("efiBoletoWebClient") WebClient webClient,
		EfiBoletoAuthGateway authGateway,
		EfiBoletoProperties props,
		BoletoMetrics metrics,
		CacheManager cacheManager
	) {
		this.webClient = webClient;
		this.authGateway = authGateway;
		this.props = props;
		this.metrics = metrics;
		this.cacheManager = cacheManager;
	}

	@Override
	public BoletoIssuanceResponse issue (BoletoIssuanceRequest request) {
		log.info("Calling Efí Bank POST /v1/charge/one-step payerDocument={} amount={}",
			request.payerDocument(), request.amount());

		return metrics.timeGatewayCall("issueBoleto", () -> {
			var token = authGateway.getAccessToken();
			var body = toEfiRequest(request);

			var response = webClient.post()
				.uri("/v1/charge/one-step")
				.header("Authorization", "Bearer " + token)
				.bodyValue(body)
				.retrieve()
				.onStatus(HttpStatusCode::isError, clientResponse ->
					clientResponse
						.bodyToMono(String.class)
						.flatMap(errorBody -> {
							if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED) {
								evictTokenCache();
							}

							log.error("Efí Bank issueBoleto error: status={} body={}", clientResponse.statusCode(), errorBody);

							return Mono.error(new BoletoGatewayException(
								"Efí Bank returned %s for issueBoleto: %s".formatted(clientResponse.statusCode(), errorBody)
							));
						})
				)
				.bodyToMono(EfiIssueBoletoResponse.class)
				.block(Duration.ofSeconds(30));

			if (response == null || response.data() == null) {
				throw new BoletoGatewayException("Efí Bank returned empty body for issueBoleto");
			}

			var data = response.data();
			var pdfUrl = data.pdf() != null ? data.pdf().charge() : null;

			log.info("Efí Bank issueBoleto success: chargeId={} status={}", data.chargeId(), data.status());

			metrics.recordBoletoIssued();

			return new BoletoIssuanceResponse(
				data.chargeId(),
				data.barcode(),
				data.billetLink(),
				pdfUrl
			);
		});
	}

	@Override
	public String getChargeStatus (long chargeId) {
		log.debug("Calling Efí Bank GET /v1/charge/{}", chargeId);

		return metrics.timeGatewayCall("getChargeStatus", () -> {
			var token = authGateway.getAccessToken();

			var response = webClient.get()
				.uri(props.baseUrl() + "/v1/charge/{id}", chargeId)
				.header("Authorization", "Bearer " + token)
				.retrieve()
				.onStatus(HttpStatusCode::isError, clientResponse ->
					clientResponse
						.bodyToMono(String.class)
						.flatMap(errorBody -> {
							if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED) {
								evictTokenCache();
							}

							return Mono.error(new BoletoGatewayException(
								"Efí Bank getChargeStatus error %s: %s".formatted(clientResponse.statusCode(), errorBody)
							));
						})
				)
				.bodyToMono(EfiGetChargeDetailResponse.class)
				.block(Duration.ofSeconds(15));

			if (response == null || response.data() == null) {
				throw new BoletoGatewayException("Efí Bank returned empty body for getChargeStatus chargeId=" + chargeId);
			}

			log.debug("Efí Bank getChargeStatus chargeId={} status={}", chargeId, response.data().status());

			return response.data().status();
		});
	}

	private EfiIssueBoletoRequest toEfiRequest (BoletoIssuanceRequest req) {
		var customer = req.payerDocument().length() == CNPJ_LENGTH
			? EfiIssueBoletoRequest.Customer.cnpj(req.payerName(), req.payerDocument())
			: EfiIssueBoletoRequest.Customer.cpf(req.payerName(), req.payerDocument());

		int amountInCents = req.amount()
			.multiply(BigDecimal.valueOf(100))
			.setScale(0, RoundingMode.HALF_UP)
			.intValueExact();

		var billet = new EfiIssueBoletoRequest.BankingBillet(
			customer,
			req.dueDate().format(DATE_FORMAT),
			req.description()
		);

		var metadata = new EfiIssueBoletoRequest.Metadata(req.notificationUrl(), null);

		var item = new EfiIssueBoletoRequest.Item(req.description(), 1, amountInCents);

		return new EfiIssueBoletoRequest(
			List.of(item),
			metadata,
			new EfiIssueBoletoRequest.Payment(billet)
		);
	}

	private void evictTokenCache () {
		var cache = cacheManager.getCache("efi-cobrancas-oauth-token");

		if (cache != null) {
			cache.evict("access_token");
			log.info("Evicted Efí Cobranças OAuth2 token from cache after 401 response");
		}
	}
}
