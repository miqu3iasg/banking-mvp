package com.miqu3iasg.banking.transaction.service;

import com.miqu3iasg.banking.shared.domain.Money;
import com.miqu3iasg.banking.transaction.domain.Transaction;
import com.miqu3iasg.banking.transaction.domain.TransactionStatus;
import com.miqu3iasg.banking.transaction.domain.TransactionType;

import java.time.Instant;
import java.util.UUID;

public record TransactionCompletedEvent(
	UUID transactionId,
	UUID accountId,
	UUID counterpartAccountId,
	TransactionType type,
	TransactionStatus status,
	Money amount,
	String description,
	String referenceId,
	Instant occurredAt
) {
	public static TransactionCompletedEvent ofSingleAccount (Transaction transaction) {
		return new TransactionCompletedEvent(
			transaction.getId(),
			transaction.getAccountId(),
			null,
			transaction.getType(),
			transaction.getStatus(),
			transaction.getAmount(),
			transaction.getDescription(),
			null,
			Instant.now()
		);
	}

	public static TransactionCompletedEvent ofTransferLeg (Transaction transaction, UUID counterpartAccountId) {
		return new TransactionCompletedEvent(
			transaction.getId(),
			transaction.getAccountId(),
			counterpartAccountId,
			transaction.getType(),
			transaction.getStatus(),
			transaction.getAmount(),
			transaction.getDescription(),
			transaction.getReferenceId(),
			Instant.now()
		);
	}

}
