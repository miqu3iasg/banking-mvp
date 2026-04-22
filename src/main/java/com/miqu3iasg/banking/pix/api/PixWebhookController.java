package com.miqu3iasg.banking.pix.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miqu3iasg.banking.pix.gateway.dto.EfiWebhookPayload;
import com.miqu3iasg.banking.pix.metrics.PixMetrics;
import com.miqu3iasg.banking.pix.service.PixService;
import com.miqu3iasg.banking.shared.config.WebhookMtlsConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Slf4j
@ConditionalOnProperty(name = "efi.webclient.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequiredArgsConstructor
public class PixWebhookController {

	private final PixService pixService;
	private final PixMetrics pixMetrics;
	private final ObjectMapper objectMapper;

	/**
	 * Registration probe endpoint.
	 * When registering a webhook via PUT /v2/webhook/:chave, Efí sends a test request
	 * to the root URL before appending /pix. This endpoint handles that probe.
	 * Must respond 200 — if it doesn't, webhook registration fails.
	 */
	@PostMapping("/v1/pix/webhook")
	public ResponseEntity<Void> webhookProbe () {
		log.debug("Efí Bank webhook registration probe received");
		return ResponseEntity.ok().build();
	}

	@PostMapping("/v1/pix/webhook/pix")
	public ResponseEntity<Void> receiveWebhook (@RequestBody byte[] rawBody, HttpServletRequest request) {
		String remoteIp = request.getHeader("X-Forwarded-For");

		if (remoteIp == null) remoteIp = request.getRemoteAddr();

		if (!WebhookMtlsConfig.EFI_WEBHOOK_IP.equals(remoteIp)) {
			log.warn("Webhook rejected: unexpected source IP={}", remoteIp);

			pixMetrics.recordWebhookRejected("ip_not_allowed");

			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		EfiWebhookPayload payload;

		try {
			payload = objectMapper.readValue(rawBody, EfiWebhookPayload.class);
		} catch (Exception e) {
			log.warn("Failed to parse incoming webhook payload: {}", e.getMessage());

			pixMetrics.recordWebhookRejected("deserialization_failed");

			return ResponseEntity.badRequest().build();
		}

		if (payload.payments() == null || payload.payments().isEmpty()) {
			log.debug("Webhook received with empty payments array; acknowledging with 200");
			return ResponseEntity.ok().build();
		}

		log.debug("Webhook received: {} payment event(s)", payload.payments().size());

		for (var pixEvent : payload.payments()) {
			try {
				String idempotencyKey = "webhook:" + pixEvent.endToEndId();

				pixService.processWebhookPayment(
					pixEvent.txid(),
					Instant.parse(pixEvent.timestamp()),
					idempotencyKey
				);

			} catch (Exception e) {
				log.error("Webhook processing failed for txid={} endToEndId={}",
					pixEvent.txid(),
					pixEvent.endToEndId(),
					e);

				pixMetrics.recordWebhookRejected("processing_failed");

				return ResponseEntity.internalServerError().build();
			}
		}

		return ResponseEntity.ok().build();
	}
}
