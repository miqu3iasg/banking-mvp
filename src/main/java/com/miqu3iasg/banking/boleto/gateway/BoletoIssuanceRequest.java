package com.miqu3iasg.banking.boleto.gateway;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BoletoIssuanceRequest(
	String payerName,
	String payerDocument,
	BigDecimal amount,
	LocalDate dueDate,
	String description,
	String notificationUrl
) { }
