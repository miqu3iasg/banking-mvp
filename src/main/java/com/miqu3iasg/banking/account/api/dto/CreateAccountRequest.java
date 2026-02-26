package com.miqu3iasg.banking.account.api.dto;

import com.miqu3iasg.banking.account.domain.AccountType;
import com.miqu3iasg.banking.shared.exception.InvalidRequestException;
import com.miqu3iasg.banking.shared.exception.code.CustomerFaultCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.regex.Pattern;

@Schema(description = "Request body for creating a new bank account")
public record CreateAccountRequest(

	@Schema(description = "Full name of the account holder", example = "John Doe")
	@NotBlank(message = "holderName is required")
	@Size(max = 255, message = "holderName must not exceed 255 characters")
	String holderName,

	@Schema(description = "CPF or CNPJ document number (digits only)", example = "12345678901")
	@NotBlank(message = "documentNumber is required")
	String documentNumber,

	@Schema(description = "Type of account (e.g. CHECKING, SAVINGS)")
	@NotNull(message = "type is required")
	AccountType type,

	@Schema(description = "Account holder's email address", example = "john.doe@example.com")
	@NotBlank(message = "email is required")
	@Email(message = "email must be a valid address")
	String email
) {
	public static final Pattern VALID_EMAIL_ADDRESS_REGEX = Pattern.compile(
		"^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$",
		Pattern.CASE_INSENSITIVE
	);

	public CreateAccountRequest {
		requireNonBlank(holderName, "holderName must not be blank", CustomerFaultCode.CUSTOMER_INVALID_INPUT);
		requireNonBlank(email, "email must not be blank", CustomerFaultCode.CUSTOMER_INVALID_EMAIL);
		requireNonBlank(documentNumber, "documentNumber must not be blank", CustomerFaultCode.CUSTOMER_INVALID_DOCUMENT);

		if (email.contains(" ") || !VALID_EMAIL_ADDRESS_REGEX.matcher(email).matches()) {
			throw new InvalidRequestException(CustomerFaultCode.CUSTOMER_INVALID_EMAIL);
		}

		if (type == null) {
			throw new InvalidRequestException("accountType must not be null", CustomerFaultCode.CUSTOMER_INVALID_INPUT
			);
		}
	}

	private static void requireNonBlank (String value, String message, CustomerFaultCode faultCode) {
		if (value == null || value.isBlank()) {
			throw new InvalidRequestException(message, faultCode);
		}
	}
}
