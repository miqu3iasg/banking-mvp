package com.miqu3iasg.banking.boleto.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body from {@code GET /v1/charge/:id} (Efí Bank Cobranças API).
 * Used to verify the current status of a charge after receiving a webhook notification token.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EfiGetChargeDetailResponse(
	int code,
	Data data
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Data(
		@JsonProperty("charge_id") long chargeId,
		String status
	) { }
}
