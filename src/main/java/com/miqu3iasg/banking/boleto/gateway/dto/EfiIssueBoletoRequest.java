package com.miqu3iasg.banking.boleto.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request body for {@code POST /v1/charge/one-step} (Efí Bank Cobranças API).
 * <p>
 * Amount is represented in <strong>cents</strong> (integer), e.g. R$ 59.90 → 5990.
 * Customer can be a natural person (CPF) or a legal entity (CNPJ via juridical_person).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EfiIssueBoletoRequest(
	List<Item> items,
	Metadata metadata,
	Payment payment
) {

	// TODO: fix this
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Item(
		String name,
		int value,
		int amount
	) { }

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Metadata(
		@JsonProperty("notification_url") String notificationUrl,
		@JsonProperty("custom_id") String customId
	) { }

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Payment(
		@JsonProperty("banking_billet") BankingBillet bankingBillet
	) { }

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record BankingBillet(
		Customer customer,
		@JsonProperty("expire_at") String expireAt,
		String message
	) { }

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Customer(
		String name,
		String cpf,
		@JsonProperty("juridical_person") JuridicalPerson juridicalPerson
	) {
		/**
		 * Factory for CPF (natural person) customer.
		 */
		public static Customer cpf (String name, String cpf) {
			return new Customer(name, cpf, null);
		}

		/**
		 * Factory for CNPJ (legal entity) customer.
		 */
		public static Customer cnpj (String corporateName, String cnpj) {
			return new Customer(null, null, new JuridicalPerson(corporateName, cnpj));
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record JuridicalPerson(
		@JsonProperty("corporate_name") String corporateName,
		String cnpj
	) { }
}
