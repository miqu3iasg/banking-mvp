package com.miqu3iasg.banking.pix.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EfiGetChargeResponse(

	String txid,

	/*
	  Possible values:
	    "ATIVA"                          — pending payment (maps to our PENDING)
	    "CONCLUIDA"                      — paid (maps to our PAID)
	    "REMOVIDA_PELO_USUARIO_RECEBEDOR"— cancelled by us (maps to our CANCELLED)
	    "REMOVIDA_PELO_PSP"              — cancelled by Efí (treat as CANCELLED)
	 */
	String status,

	@JsonProperty("pixCopiaECola")
	String copyPaste,

	String location,

	@JsonProperty("revisao") int revision,
	@JsonProperty("pix") List<PixPayment> payments
) {

	/**
	 * Payment details embedded in the charge when status is CONCLUIDA.
	 * Mirrors the webhook payload structure.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PixPayment(
		String endToEndId,
		String txid,
		@JsonProperty("valor") String amount,
		@JsonProperty("horario") String timestamp,
		@JsonProperty("infoPagador") String payerInfo
	) { }

	public boolean isCompleted () {
		return "CONCLUIDA".equals(status);
	}

	public boolean isRemoved () {
		return "REMOVIDA_PELO_USUARIO_RECEBEDOR".equals(status) || "REMOVIDA_PELO_PSP".equals(status);
	}
}
