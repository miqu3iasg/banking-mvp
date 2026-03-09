package com.miqu3iasg.banking.transaction.api.dto;

import com.miqu3iasg.banking.transaction.domain.Transaction;
import com.miqu3iasg.banking.transaction.domain.TransactionStatus;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Representation of a completed transaction entry")
public record TransactionResponse(

	@Schema(description = "Unique transaction ID")
	UUID transactionId,

	@Schema(description = "Account that owns this ledger entry")
	UUID accountId,

	@Schema(description = "Counterpart account for transfers; null for deposits/withdrawals")
	UUID counterpartAccountId,

	@Schema(description = "Transaction amount", example = "250.00")
	BigDecimal amount,

	@Schema(description = "ISO 4217 currency code", example = "BRL")
	String currency,

	@Schema(description = "Type of movement")
	TransactionType type,

	@Schema(description = "Current lifecycle status")
	TransactionStatus status,

	@Schema(description = "Human-readable description")
	String description,

	@Schema(description = "UTC timestamp when this entry was created")
	Instant createdAt
) {

	public static TransactionResponse from (Transaction transaction) {
		return new TransactionResponse(
			transaction.getId(),
			transaction.getAccountId(),
			transaction.getCounterpartAccountId(),
			transaction.getAmount().amount(),
			transaction.getAmount().currency().getCurrencyCode(),
			transaction.getType(),
			transaction.getStatus(),
			transaction.getDescription(),
			transaction.getCreatedAt()
		);
	}
}
