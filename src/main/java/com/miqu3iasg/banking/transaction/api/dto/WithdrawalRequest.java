package com.miqu3iasg.banking.transaction.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Request body for a withdrawal operation")
public record WithdrawalRequest(

	@Schema(description = "ID of the account to debit")
	@NotNull(message = "accountId is required")
	UUID accountId,

	@Schema(description = "Amount to withdraw — must be strictly positive")
	@NotNull(message = "amount is required")
	@Positive(message = "amount must be positive")
	BigDecimal amount,

	@Schema(description = "ISO 4217 currency code", example = "BRL")
	@NotBlank(message = "currency is required")
	@Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
	String currency,

	@Size(max = 255)
	String description
) { }
