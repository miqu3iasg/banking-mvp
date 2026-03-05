package com.miqu3iasg.banking.pix.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePixChargeRequest(
	@NotNull
	@DecimalMin(value = "0.01", message = "PIX charge amount must be at least R$0.01")
	@Digits(integer = 15, fraction = 2, message = "Amount must have at most 2 decimal places")
	BigDecimal amount,

	@Size(max = 200, message = "Payer name must not exceed 200 characters")
	String payerName,

	String payerCpfCnpj
) {
}
