package com.miqu3iasg.banking.transaction.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Request body for a deposit operation")
public record DepositRequest(

	@Schema(description = "ID of the account to credit", example = "550e8400-e29b-41d4-a716-446655440000")
	@NotNull(message = "accountId is required")
	UUID accountId,

	@Schema(description = "Amount to deposit — must be strictly positive", example = "150.00")
	@NotNull(message = "amount is required")
	@Positive(message = "amount must be positive")
	BigDecimal amount,

	@Schema(description = "ISO 4217 currency code", example = "BRL")
	@NotBlank(message = "currency is required")
	@Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
	String currency,

	@Schema(description = "Optional human-readable description", example = "Salary deposit")
	@Size(max = 255)
	String description
) { }
