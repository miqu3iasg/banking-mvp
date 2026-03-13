package com.miqu3iasg.banking.boleto.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
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
	String description

) { }
