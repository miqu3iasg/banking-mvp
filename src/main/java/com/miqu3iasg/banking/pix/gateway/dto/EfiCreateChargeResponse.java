package com.miqu3iasg.banking.pix.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public record EfiCreateChargeResponse(
	String txid,
	@JsonProperty("revisao") int revision,
	String status,
	@JsonProperty("loc") Loc loc,
	String location,

	@JsonProperty("pixCopiaECola")
	String copyPaste,

	@JsonProperty("calendario") Schedule schedule
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Loc(
		int id,
		String location,
		@JsonProperty("tipoCob") String chargeType
		// "cob" for immediate charges, "cobv" for due charges
	) { }

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Schedule(
		@JsonProperty("criacao") String createdAt,
		@JsonProperty("expiracao") int expiration
	) { }

	public String resolveLocation () {
		if (loc != null && loc.location() != null) return loc.location();
		return location;
	}
}
