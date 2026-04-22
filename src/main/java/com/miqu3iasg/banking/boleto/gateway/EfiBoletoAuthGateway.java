package com.miqu3iasg.banking.boleto.gateway;

import com.miqu3iasg.banking.boleto.config.EfiBoletoProperties;
import com.miqu3iasg.banking.boleto.exception.BoletoGatewayException;
import com.miqu3iasg.banking.boleto.gateway.dto.EfiTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Map;

@Slf4j
@ConditionalOnProperty(name = "efi.webclient.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class EfiBoletoAuthGateway {
	private final WebClient webClient;
	private final EfiBoletoProperties props;

	public EfiBoletoAuthGateway (
		@Qualifier("efiBoletoWebClient") WebClient webClient,
		EfiBoletoProperties props
	) {
		this.webClient = webClient;
		this.props = props;
	}

	@Cacheable(value = "efi-cobrancas-oauth-token", key = "'access_token'")
	public String getAccessToken () {
		log.debug("Cache miss: fetching new Efí Cobranças OAuth2 access token");

		try {
			var authHeader = buildBasicAuthHeader();

			Map<String, String> body = Map.of("grant_type", "client_credentials");

			var response = webClient.post()
				.uri("/v1/authorize")
				.header("Authorization", authHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.retrieve()
				.onStatus(status -> !status.is2xxSuccessful(), clientResponse -> {
					log.error("Failed to fetch Efí Cobranças access token: HTTP {}", clientResponse.statusCode());

					return clientResponse
						.bodyToMono(String.class)
						.flatMap(errorBody -> Mono.error(new BoletoGatewayException(
								"Failed to fetch OAuth2 token from Efí Cobranças: %s".formatted(errorBody)
							)
						));
				})
				.bodyToMono(EfiTokenResponse.class)
				.block();

			if (response == null || response.accessToken() == null) {
				throw new BoletoGatewayException("Efí Cobranças returned an empty token response");
			}

			log.info("Efí Cobranças OAuth2 token obtained successfully, expires_in={}s", response.expiresIn());

			return response.accessToken();
		} catch (BoletoGatewayException exception) {
			throw exception;
		} catch (Exception exception) {
			log.error("Exception while fetching Efí Cobranças access token", exception);

			throw new BoletoGatewayException(
				"Unexpected error obtaining Cobranças OAuth2 token: %s".formatted(exception.getMessage()),
				exception
			);
		}
	}

	private String buildBasicAuthHeader () {
		var credentials = "%s:%s".formatted(props.clientId(), props.clientSecret());

		var encoded = Base64.getEncoder().encodeToString(credentials.getBytes());

		return "Basic %s".formatted(encoded);
	}
}
