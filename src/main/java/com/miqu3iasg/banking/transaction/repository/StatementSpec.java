package com.miqu3iasg.banking.transaction.repository;

import com.miqu3iasg.banking.transaction.domain.Transaction;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class StatementSpec {

	private StatementSpec () { }

	public static Specification<Transaction> build (
		UUID accountId, Instant from, Instant to, TransactionType type) {

		return Specification
			.where(accountIdEquals(accountId))
			.and(createdAfter(from))
			.and(createdBefore(to))
			.and(typeEquals(type));
	}

	private static Specification<Transaction> accountIdEquals (UUID accountId) {
		return (root, query, cb) -> cb.equal(root.get("accountId"), accountId);
	}

	private static Specification<Transaction> createdAfter (Instant from) {
		return (root, query, cb) -> from == null ? null
			: cb.greaterThanOrEqualTo(root.get("createdAt"), from);
	}

	private static Specification<Transaction> createdBefore (Instant to) {
		return (root, query, cb) -> to == null ? null
			: cb.lessThanOrEqualTo(root.get("createdAt"), to);
	}

	private static Specification<Transaction> typeEquals (TransactionType type) {
		return (root, query, cb) -> type == null ? null
			: cb.equal(root.get("type"), type);
	}
}
