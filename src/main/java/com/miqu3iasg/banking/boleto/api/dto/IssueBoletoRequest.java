package com.miqu3iasg.banking.boleto.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.miqu3iasg.banking.boleto.domain.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record IssueBoletoRequest(
	@NotNull(message = "Account ID is required")
	UUID accountId,

	@NotBlank(message = "Payer name is required")
	String payerName,

	@NotBlank(message = "Payer document (CPF or CNPJ) is required")
	String payerDocument,

	@NotNull(message = "Amount is required")
	@DecimalMin(value = "0.01", message = "Amount must be greater than zero")
	@Digits(integer = 13, fraction = 2, message = "Amount must have at most 2 decimal places")
	BigDecimal amount,

	@NotNull(message = "Due date is required")
	@Future(message = "Due date must be in the future")
	@JsonFormat(pattern = "yyyy-MM-dd")
	LocalDate dueDate,

	@NotBlank(message = "Description is required")
	@Size(max = 255, message = "Description must not exceed 255 characters")
	String description,

	@NotNull(message = "Address is required")
	@Valid
	AddressRequest address
) {
	public record AddressRequest(
		@NotBlank(message = "Street is required")
		@Size(max = 200)
		String street,

		@NotBlank(message = "Number is required")
		@Size(max = 20)
		String number,

		@NotBlank(message = "Neighborhood is required")
		@Size(max = 100)
		String neighborhood,

		@NotBlank(message = "Zipcode is required")
		@Pattern(regexp = "\\d{8}", message = "Zipcode must be 8 digits")
		String zipcode,

		@NotBlank(message = "City is required")
		@Size(max = 100)
		String city,

		@NotBlank(message = "State is required")
		@Pattern(regexp = "[A-Z]{2}", message = "State must be a 2-letter Brazilian UF (e.g. SP)")
		String state
	) {
		public Address toDomain () {
			return Address.of(street, number, neighborhood, zipcode, city, state);
		}
	}
}
