package com.miqu3iasg.banking.pix.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EfiWebhookPayload(@JsonProperty("pix") List<PixEvent> payments) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PixEvent(

		/*
		  End-to-end transaction ID from BACEN's payment infrastructure.
		  Used as the idempotency key for webhook processing — unique per payment event.
		  Format: E{8-digit PSP ISPB}{14-char timestamp}{random suffix}
		 */
		String endToEndId,

		String txid,

		/*
		  The PIX key that received the payment — matches the "chave" in the charge request.
		  Used for routing in Efí's infrastructure; not needed for our business logic here.
		 */
		@JsonProperty("chave") String key,
		@JsonProperty("valor") String amount,
		@JsonProperty("horario") String timestamp,
		@JsonProperty("infoPagador") String payerInfo,
		@JsonProperty("gnExtras") BankExtras extras
	) { }

	/**
	 * Efí Bank proprietary extensions, present on some callbacks depending on account settings.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record BankExtras(@JsonProperty("pagador") PayerDetails payer,
	                         @JsonProperty("tarifa") String fee) {

		@JsonIgnoreProperties(ignoreUnknown = true)
		public record PayerDetails(
			@JsonProperty("nome") String name,
			String cpf,
			String cnpj,
			@JsonProperty("codigoBanco") String bankCode
		) { }
	}
}
