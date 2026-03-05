package com.miqu3iasg.banking.boleto.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.miqu3iasg.banking.boleto.domain.Boleto;
import com.miqu3iasg.banking.boleto.domain.BoletoStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record IssueBoletoResponse(
	UUID id,
	long providerChargeId,
	String payerName,
	String payerDocument,
	BigDecimal amount,
	@JsonFormat(pattern = "yyyy-MM-dd")
	LocalDate dueDate,
	String description,
	String barcode,
	String billetLink,
	String pdfUrl,
	BoletoStatus status
) {
	public static IssueBoletoResponse from (Boleto boleto) {
		return new IssueBoletoResponse(
			boleto.getId(),
			boleto.getProviderChargeId(),
			boleto.getPayerName(),
			boleto.getPayerDocument(),
			boleto.getAmount(),
			boleto.getDueDate(),
			boleto.getDescription(),
			boleto.getBarcode(),
			boleto.getBilletLink(),
			boleto.getPdfUrl(),
			boleto.getStatus()
		);
	}
}
