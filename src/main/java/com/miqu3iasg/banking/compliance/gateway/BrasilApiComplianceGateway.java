package com.miqu3iasg.banking.compliance.gateway;

import com.miqu3iasg.banking.compliance.exception.CnpjNotFoundException;
import com.miqu3iasg.banking.compliance.exception.ComplianceServiceUnavailableException;
import com.miqu3iasg.banking.compliance.gateway.dto.BrasilApiCnpjResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
public class BrasilApiComplianceGateway implements ComplianceGateway {
	private final WebClient webClient;

	public BrasilApiComplianceGateway (
		@Qualifier("brasilApiWebClient") WebClient webClient
	) {
		this.webClient = webClient;
	}

	@Override
	public BrasilApiCnpjResponse fetchCnpj (String cnpj) {
		var response = webClient.get()
			.uri("/cnpj/v1/{cnpj}", cnpj)
			.retrieve()
			.onStatus(
				status -> status == HttpStatus.NOT_FOUND,
				clientResponse -> Mono.error(new CnpjNotFoundException(cnpj))
			)
			.onStatus(
				status -> status == HttpStatus.BAD_REQUEST,
				clientResponse -> clientResponse
					.bodyToMono(String.class)
					.flatMap(body -> Mono.error(
						new ComplianceServiceUnavailableException("BrasilAPI rejected the request: " + body)
					))
			)
			.onStatus(
				HttpStatusCode::isError,
				clientResponse -> clientResponse
					.bodyToMono(String.class)
					.flatMap(body -> {
						log.error("BrasilAPI error fetching CNPJ {}: status={} body={}",
							cnpj,
							clientResponse.statusCode(),
							body);

						return Mono.error(
							new ComplianceServiceUnavailableException(
								"BrasilAPI returned %s: %s".formatted(clientResponse.statusCode(), body)
							)
						);
					})
			)
			.bodyToMono(BrasilApiCnpjResponse.class)
			.block(Duration.ofSeconds(8));

		if (response == null) {
			throw new ComplianceServiceUnavailableException("BrasilAPI returned an empty response for CNPJ " + cnpj);
		}

		log.debug("BrasilAPI CNPJ fetch success: cnpj={}", cnpj);

		return response;
	}
}
