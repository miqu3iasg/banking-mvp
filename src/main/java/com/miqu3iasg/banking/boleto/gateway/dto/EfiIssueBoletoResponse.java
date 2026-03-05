package com.miqu3iasg.banking.boleto.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body from {@code POST /v1/charge/one-step} (Efí Bank Cobranças API).
 * <p>
 * The outer wrapper has {@code code} and {@code data}. We deserialize directly into
 * the {@code data} object after extracting it.
 * <p>
 * Example successful response:
 * <pre>{@code
 * {
 *   "code": 200,
 *   "data": {
 *     "charge_id": 12345,
 *     "barcode": "00000.00000 00000.000000 ...",
 *     "billet_link": "https://...",
 *     "pdf": { "charge": "https://..." },
 *     "status": "waiting"
 *   }
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EfiIssueBoletoResponse(
	int code,
	Data data
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Data(
		@JsonProperty("charge_id") long chargeId,
		String barcode,
		@JsonProperty("billet_link") String billetLink,
		Pdf pdf,
		@JsonProperty("expire_at") String expireAt,
		String status
	) { }

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Pdf(
		String charge
	) { }
}
