package com.miqu3iasg.banking.boleto.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miqu3iasg.banking.boleto.gateway.dto.EfiBoletoWebhookPayload;
import com.miqu3iasg.banking.boleto.metrics.BoletoMetrics;
import com.miqu3iasg.banking.boleto.service.BoletoService;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@ConditionalOnProperty(name = "efi.webclient.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequiredArgsConstructor
@Tag(name = "Boleto Webhook", description = "Endpoints for receiving payment notifications from Efí Bank")
public class BoletoWebhookController {

    private final BoletoService boletoService;
    private final BoletoMetrics boletoMetrics;
    private final ObjectMapper objectMapper;

    /**
     * Registration probe endpoint.
     * Efí sends a test request to the root URL before appending the event path.
     * Must respond 200 OK for registration to succeed.
     */
    @Operation(summary = "Webhook probe", description = "Used by Efí Bank to verify the webhook URL during registration")
    @ApiResponse(responseCode = "200", description = "Registration probe successful")
    @PostMapping("/v1/boleto/webhook")
    public ResponseEntity<Void> webhookProbe() {
        log.debug("Efí Bank boleto webhook registration probe received");
        return ResponseEntity.ok().build();
    }

    /**
     * Main payment notification endpoint.
     */
    @Operation(summary = "Receive boleto payment notification", description = "Processes payment events sent by Efí Bank")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notification processed successfully"),
        @ApiResponse(responseCode = "400", description = "Malformed payload"),
        @ApiResponse(responseCode = "403", description = "Invalid source IP"),
        @ApiResponse(responseCode = "500", description = "Internal processing error")
    })
    @PostMapping("/v1/boleto/webhook/payment")
    public ResponseEntity<Void> receiveWebhook(@RequestBody byte[] rawBody, HttpServletRequest request) {
        String remoteIp = request.getHeader("X-Forwarded-For");
        if (remoteIp == null) {
            remoteIp = request.getRemoteAddr();
        }

        if (!WebhookMtlsConfig.EFI_WEBHOOK_IP.equals(remoteIp)) {
            log.warn("Boleto webhook rejected: unexpected source IP={}", remoteIp);
            boletoMetrics.recordWebhookRejected("ip_not_allowed");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        EfiBoletoWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, EfiBoletoWebhookPayload.class);
        } catch (Exception e) {
            log.warn("Failed to parse boleto webhook payload: {}", e.getMessage());
            boletoMetrics.recordWebhookRejected("parsing_failed");
            return ResponseEntity.badRequest().build();
        }

        try {
            String idempotencyKey = "webhook:boleto:" + payload.providerChargeId();
            boletoService.processWebhookPayment(
                payload.providerChargeId(),
                payload.receivedAt(),
                idempotencyKey
            );
        } catch (Exception e) {
            log.error("Boleto webhook processing failed for chargeId={}", payload.providerChargeId(), e);
            boletoMetrics.recordWebhookRejected("processing_failed");
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok().build();
    }
}
