package com.miqu3iasg.banking.transaction.service;

import com.miqu3iasg.banking.transaction.domain.TransactionType;

import java.time.Instant;
import java.util.UUID;

public record TransactionCompletedEvent(
	UUID transactionId,
	UUID accountId,
	TransactionType type,
	Instant occurredAt
) {
	public TransactionCompletedEvent(UUID transactionId, UUID accountId, TransactionType type) {
		this(transactionId, accountId, type, Instant.now());
	}
}
