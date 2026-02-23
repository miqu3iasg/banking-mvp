package com.miqu3iasg.banking.account.api.dto;

import com.miqu3iasg.banking.account.domain.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for creating a new bank account")
public record CreateAccountRequest(

	@Schema(description = "Full name of the account holder", example = "John Doe")
	@NotBlank(message = "holderName is required")
	@Size(max = 255, message = "holderName must not exceed 255 characters")
	String holderName,

	@Schema(description = "CPF or CNPJ document number (digits only)", example = "12345678901")
	@NotBlank(message = "documentNumber is required")
	@Pattern(regexp = "\\d{11}|\\d{14}", message = "documentNumber must be an 11-digit CPF or 14-digit CNPJ")
	String documentNumber,

	@Schema(description = "Type of account (e.g. CHECKING, SAVINGS)")
	@NotNull(message = "type is required")
	AccountType type,

	@Schema(description = "Account holder's email address", example = "john.doe@example.com")
	@NotBlank(message = "email is required")
	@Email(message = "email must be a valid address")
	String email
) { }
