package com.miqu3iasg.banking.pix.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PixChargeCreationResponse(
	String txid,
	String location,
	@JsonProperty("pixCopiaECola") String copyPaste
) {
}
