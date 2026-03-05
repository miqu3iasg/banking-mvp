package com.miqu3iasg.banking.pix.gateway;

import com.miqu3iasg.banking.boleto.gateway.dto.EfiTokenResponse;
import com.miqu3iasg.banking.pix.config.EfiPixProperties;
import com.miqu3iasg.banking.pix.exception.PixGatewayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class EfiPixAuthGateway {
	private final WebClient webClient;
	private final EfiPixProperties props;

	public EfiPixAuthGateway (
		@Qualifier("efiPixWebClient") WebClient webClient,
		EfiPixProperties props
	) {
		this.webClient = webClient;
		this.props = props;
	}

	@Cacheable(value = "efi-oauth-token", key = "'access_token'")
	public String getAccessToken () {
		log.debug("Cache miss: fetching new Efí Bank OAuth2 access token");

		try {
			var authHeader = buildBasicAuthHeader();

			Map<String, String> body = Map.of("grant_type", "client_credentials");

			var response = webClient.post()
				.uri("/oauth/token")
				.header("Authorization", authHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.retrieve()
				.onStatus(status -> !status.is2xxSuccessful(), clientResponse -> {
					log.error("Failed to fetch Efí Bank access token: HTTP {}", clientResponse.statusCode());

					return clientResponse.bodyToMono(String.class)
						.flatMap(errorBody -> Mono.error(new PixGatewayException(
							"Failed to fetch OAuth2 token from Efí Bank: %s".formatted(errorBody)))
						);
				})
				.bodyToMono(EfiTokenResponse.class)
				.block();

			if (response == null || response.accessToken() == null) {
				log.error("Invalid response when fetching Efí Bank access token: {}", response);

				throw new PixGatewayException("Efí Bank returned an empty token response");
			}

			log.info("Efí Bank OAuth2 token obtained successfully, expires_in={}s", response.expiresIn());

			return response.accessToken();
		} catch (PixGatewayException e) {
			throw e;
		} catch (Exception e) {
			log.error("Exception while fetching Efí Bank access token", e);

			throw new PixGatewayException("Unexpected error obtaining OAuth2 token: %s".formatted(e.getMessage()), e);
		}
	}

	private String buildBasicAuthHeader () {
		var credentials = "%s:%s".formatted(props.clientId(), props.clientSecret());

		var encoded = Base64.getEncoder().encodeToString(credentials.getBytes());

		return "Basic %s".formatted(encoded);
	}
}
