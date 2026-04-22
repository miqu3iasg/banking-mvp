package com.miqu3iasg.banking.pix.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.miqu3iasg.banking.pix.config.EfiPixProperties;
import com.miqu3iasg.banking.pix.domain.PixKeyStatus;
import com.miqu3iasg.banking.pix.gateway.EfiPixAuthGateway;
import com.miqu3iasg.banking.pix.repository.PixKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Service
@ConditionalOnProperty(name = "efi.webclient.enabled", havingValue = "true", matchIfMissing = true)
public class WebhookRegistrationService {
	private final PixKeyRepository keyRepository;
	private final EfiPixAuthGateway efiPixAuthGateway;
	private final EfiPixProperties props;
	private final WebClient webClient;

	public WebhookRegistrationService (
		PixKeyRepository keyRepository,
		EfiPixAuthGateway efiPixAuthGateway,
		EfiPixProperties props,
		@Qualifier("efiPixWebClient") WebClient webClient
	) {
		this.keyRepository = keyRepository;
		this.efiPixAuthGateway = efiPixAuthGateway;
		this.props = props;
		this.webClient = webClient;
	}

	@Async
	@Transactional(readOnly = true)
	@EventListener(ApplicationReadyEvent.class)
	public void registerWebhookOnStartup () {
		var activeKeys = keyRepository.findAllByStatus(PixKeyStatus.ACTIVE);

		if (activeKeys.isEmpty()) {
			log.info("No active PIX keys found; skipping webhook registration.");
			return;
		}

		int registered = 0;
		int failed = 0;

		for (var key : activeKeys) {
			try {
				registerWebhook(key.getValue());

				registered++;
			} catch (Exception e) {
				log.warn("Webhook registration failed for PIX key={}: {}. Register manually via Efí Bank dashboard or retry on next startup.",
					key.getValue(),
					e.getMessage());

				failed++;
			}
		}

		log.info("Webhook registration complete. registered={} failed={}", registered, failed);
	}

	/**
	 * Registers the webhook URL for a single PIX key via PUT /v2/webhook/{chave}.
	 * <p>
	 * x-skip-mtls-checking:
	 * true  = sandbox / shared hosting (mTLS not available on the server side)
	 * false = production (Efí will verify our server certificate before registering)
	 * <p>
	 * webhookUrl must end with "?ignorar=" so that Efí's appended "/pix" suffix
	 * becomes a query parameter rather than a path segment, allowing a single route
	 * to handle both the registration probe and actual payment callbacks.
	 */
	public void registerWebhook (String pixKeyValue) {
		log.debug("Registering webhook for PIX key={}", pixKeyValue);

		var token = efiPixAuthGateway.getAccessToken();

		var body = new WebhookRegistrationRequest(props.webhookUrl());

		webClient.put()
			.uri("/v2/webhook/{chave}", pixKeyValue)
			.header("Authorization", "Bearer " + token)
			.header("x-skip-mtls-checking", props.sandbox() ? "true" : "false")
			.bodyValue(body)
			.retrieve()
			.onStatus(
				status -> status == HttpStatus.BAD_REQUEST,
				clientResponse -> clientResponse
					.bodyToMono(String.class)
					.map(errorBody -> new RuntimeException(
						"Webhook URL probe failed for key " + pixKeyValue + "; ensure the URL is reachable: " + errorBody)
					)
			)
			.onStatus(HttpStatusCode::isError,
				clientResponse -> clientResponse
					.bodyToMono(String.class)
					.map(errorBody -> new RuntimeException(
						"Webhook registration HTTP error for key " + pixKeyValue + ": " + errorBody)
					)
			)
			.bodyToMono(Void.class)
			.block(Duration.ofSeconds(15));

		log.info("Webhook registered for PIX key={} url={}", pixKeyValue, props.webhookUrl());
	}

	/**
	 * Request body for PUT /v2/webhook/:chave.
	 * Official doc example:
	 * { "webhookUrl": "https://exemplo-pix/webhook?ignorar=" }
	 */
	private record WebhookRegistrationRequest(
		@JsonProperty("webhookUrl") String webhookUrl) { }
}
