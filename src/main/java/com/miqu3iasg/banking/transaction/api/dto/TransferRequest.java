package com.miqu3iasg.banking.transaction.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Request body for an internal account-to-account transfer")
public record TransferRequest(

	@Schema(description = "Source account ID")
	@NotNull(message = "originAccountId is required")
	UUID originAccountId,

	@Schema(description = "Destination account ID")
	@NotNull(message = "destinationAccountId is required")
	UUID destinationAccountId,

	@Schema(description = "Transfer amount — must be strictly positive")
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
