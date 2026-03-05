package com.miqu3iasg.banking.pix.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.miqu3iasg.banking.shared.domain.Money;

import java.util.Locale;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EfiCreateChargeRequest(
	@JsonProperty("calendario") Schedule schedule,
	@JsonProperty("devedor") Payer payer,
	@JsonProperty("valor") Amount amount,
	@JsonProperty("chave") String key,

	@JsonProperty("solicitacaoPagador")
	String payerRequest
) {

	/**
	 * Charge expiry window.
	 */
	public record Schedule(@JsonProperty("expiracao") int expiration) { }

	/**
	 * Payer identification. Mutually exclusive CPF / CNPJ fields.
	 * Use the static factories below — never construct directly.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Payer(
		String cpf,
		String cnpj,
		@JsonProperty("nome") String name
	) {

		/**
		 * Individual payer identified by CPF (11 digits, no formatting).
		 */
		public static Payer cpf (String cpf, String name) {
			return new Payer(cpf, null, name);
		}

		/**
		 * Legal entity payer identified by CNPJ (14 digits, no formatting).
		 */
		public static Payer cnpj (String cnpj, String name) {
			return new Payer(null, cnpj, name);
		}
	}

	/**
	 * Charge amount.
	 * "original" must be a string with exactly 2 decimal places per BACEN spec.
	 *
	 * <p>{@link Money} stores amounts at scale 4 internally, so {@code toPlainString()}
	 * would produce {@code "100.0000"} — invalid per BACEN. The format call here
	 * reduces to exactly 2 decimal places on serialization only; the value object
	 * itself is not mutated.
	 *
	 * <p>Example: {@code Money.brl("123.456")} → {@code "123.46"}
	 */
	public record Amount(@JsonProperty("original") String original) {
		public static Amount of (Money amount) {
			return new Amount(String.format(Locale.US, "%.2f", amount.amount()));
		}
	}
}
