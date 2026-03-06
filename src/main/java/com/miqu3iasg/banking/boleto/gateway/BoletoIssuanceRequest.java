package com.miqu3iasg.banking.boleto.gateway;

import com.miqu3iasg.banking.boleto.domain.Boleto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BoletoIssuanceRequest(
	String payerName,
	String payerDocument,
	BigDecimal amount,
	LocalDate dueDate,
	String description,
	String notificationUrl
) {
	public static BoletoIssuanceRequest from (Boleto boleto, String document, String notificationUrl) {
		return new BoletoIssuanceRequest(
			boleto.getPayerName(),
			document,
			boleto.getAmount(),
			boleto.getDueDate(),
			boleto.getDescription(),
			notificationUrl
		);
	}
}
