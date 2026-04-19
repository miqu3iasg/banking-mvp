package com.miqu3iasg.banking.boleto.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miqu3iasg.banking.boleto.gateway.dto.EfiBoletoWebhookPayload;
import com.miqu3iasg.banking.boleto.metrics.BoletoMetrics;
import com.miqu3iasg.banking.boleto.service.BoletoService;
import com.miqu3iasg.banking.shared.config.WebhookMtlsConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoletoWebhookControllerTest {

    @Mock
    private BoletoService boletoService;

    @Mock
    private BoletoMetrics boletoMetrics;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private BoletoWebhookController controller;

    private final String VALID_IP = WebhookMtlsConfig.EFI_WEBHOOK_IP;
    private final String INVALID_IP = "1.2.3.4";

    @Test
    void probe_returnsOk() {
        ResponseEntity<Void> response = controller.webhookProbe();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void payment_rejectsInvalidIp_returnsForbidden() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(INVALID_IP);

        ResponseEntity<Void> response = controller.receiveWebhook(new byte[0], request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(boletoMetrics).recordWebhookRejected("ip_not_allowed");
        verifyNoInteractions(boletoService);
    }

    @Test
    void payment_rejectsMalformedJson_returnsBadRequest() throws Exception {
        when(request.getHeader("X-Forwarded-For")).thenReturn(VALID_IP);
        byte[] rawBody = "invalid json".getBytes();
        when(objectMapper.readValue(rawBody, EfiBoletoWebhookPayload.class)).thenThrow(new RuntimeException("Parsing failed"));

        ResponseEntity<Void> response = controller.receiveWebhook(rawBody, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(boletoMetrics).recordWebhookRejected("parsing_failed");
        verifyNoInteractions(boletoService);
    }

    @Test
    void payment_callsService_whenRequestIsValid() throws Exception {
        when(request.getHeader("X-Forwarded-For")).thenReturn(VALID_IP);
        byte[] rawBody = "{\"providerChargeId\": 123, \"receivedAt\": \"2026-04-19T10:00:00Z\"}".getBytes();
        EfiBoletoWebhookPayload payload = new EfiBoletoWebhookPayload(123L, Instant.parse("2026-04-19T10:00:00Z"));

        when(objectMapper.readValue(rawBody, EfiBoletoWebhookPayload.class)).thenReturn(payload);

        ResponseEntity<Void> response = controller.receiveWebhook(rawBody, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(boletoService).processWebhookPayment(eq(123L), eq(payload.receivedAt()), any());
    }
}
