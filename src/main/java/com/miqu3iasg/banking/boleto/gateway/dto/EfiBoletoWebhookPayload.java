package com.miqu3iasg.banking.boleto.gateway.dto;

import java.time.Instant;

/**
 * Payload received from Efí Bank Cobranças webhook.
 *
 * @param providerChargeId the unique identifier of the charge at the provider
 * @param receivedAt       the timestamp when the notification was sent
 */
public record EfiBoletoWebhookPayload(
    long providerChargeId,
    Instant receivedAt
) {}
