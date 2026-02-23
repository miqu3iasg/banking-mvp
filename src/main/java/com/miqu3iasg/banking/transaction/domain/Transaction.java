package com.miqu3iasg.banking.transaction.domain;

import com.miqu3iasg.banking.shared.domain.AuditableEntity;
import com.miqu3iasg.banking.shared.domain.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	name = "transactions",
	indexes = {
		@Index(name = "idx_transactions_account_created", columnList = "account_id, created_at"),
		@Index(name = "idx_transactions_idempotency", columnList = "idempotency_key", unique = true),
		@Index(name = "idx_transactions_reference", columnList = "reference_id")
	}
)
public class Transaction extends AuditableEntity {

	@Column(name = "account_id", nullable = false)
	private UUID accountId;

	@Column(name = "counterpart_account_id")
	private UUID counterpartAccountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransactionType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransactionStatus status;

	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false, precision = 19, scale = 4)),
		@AttributeOverride(name = "currency", column = @Column(name = "currency_code", nullable = false, length = 3))
	})
	private Money amount;

	@Column(name = "description")
	private String description;

	@Column(name = "idempotency_key", unique = true, nullable = false)
	private String idempotencyKey;

	@Column(name = "reference_id")
	private String referenceId;

	public static Transaction debit (
		UUID accountId,
		Money amount,
		String description,
		String idempotencyKey
	) {
		requireAccountId(accountId);
		requirePositiveAmount(amount);
		requireIdempotencyKey(idempotencyKey);

		return create(accountId, null, TransactionType.DEBIT, amount, description, idempotencyKey, null);
	}

	public static Transaction credit (
		UUID accountId,
		Money amount,
		String description,
		String idempotencyKey
	) {
		requireAccountId(accountId);
		requirePositiveAmount(amount);
		requireIdempotencyKey(idempotencyKey);

		return create(accountId, null, TransactionType.CREDIT, amount, description, idempotencyKey, null);
	}

	public static Transaction transfer (
		UUID fromAccountId,
		UUID toAccountId,
		Money amount,
		String description,
		String idempotencyKey
	) {
		requireAccountId(fromAccountId);

		if (toAccountId == null)
			throw new IllegalArgumentException("toAccountId is required for a transfer");

		if (fromAccountId.equals(toAccountId))
			throw new IllegalArgumentException("Cannot transfer to the same account");

		requirePositiveAmount(amount);
		requireIdempotencyKey(idempotencyKey);

		return create(fromAccountId, toAccountId, TransactionType.TRANSFER, amount, description, idempotencyKey, null);
	}

	public static Transaction pix (
		UUID accountId,
		Money amount,
		String description,
		String idempotencyKey,
		String pixTxid
	) {
		requireAccountId(accountId);
		requirePositiveAmount(amount);
		requireIdempotencyKey(idempotencyKey);
		if (pixTxid == null || pixTxid.isBlank())
			throw new IllegalArgumentException("pixTxid is required for a PIX transaction");
		return create(accountId, null, TransactionType.PIX, amount, description, idempotencyKey, pixTxid);
	}

	public static Transaction boleto (
		UUID accountId,
		Money amount,
		String description,
		String idempotencyKey,
		String chargeId
	) {
		requireAccountId(accountId);
		requirePositiveAmount(amount);
		requireIdempotencyKey(idempotencyKey);

		if (chargeId == null || chargeId.isBlank())
			throw new IllegalArgumentException("chargeId is required for a boleto transaction");

		return create(accountId, null, TransactionType.BOLETO, amount, description, idempotencyKey, chargeId);
	}

	public void fail () {
		if (this.status != TransactionStatus.PENDING) {
			throw new IllegalStateException(
				"Only PENDING transactions can be marked as failed. Current status: " + status
			);
		}
		this.status = TransactionStatus.FAILED;
	}

	public void complete () {
		if (this.status != TransactionStatus.PENDING) {
			throw new IllegalStateException(
				"Only PENDING transactions can be completed. Current status: " + status
			);
		}
		this.status = TransactionStatus.COMPLETED;
	}

	public boolean isCompleted () {
		return this.status == TransactionStatus.COMPLETED;
	}

	public boolean isPending () {
		return this.status == TransactionStatus.PENDING;
	}

	public boolean isFailed () {
		return this.status == TransactionStatus.FAILED;
	}

	private static Transaction create (
		UUID accountId,
		UUID counterpartAccountId,
		TransactionType type,
		Money amount,
		String description,
		String idempotencyKey,
		String referenceId
	) {
		Transaction t = new Transaction();
		t.accountId = accountId;
		t.counterpartAccountId = counterpartAccountId;
		t.type = type;
		t.amount = amount;
		t.description = description;
		t.idempotencyKey = idempotencyKey;
		t.referenceId = referenceId;
		t.status = TransactionStatus.COMPLETED;
		return t;
	}

	private static void requireAccountId (UUID accountId) {
		if (accountId == null)
			throw new IllegalArgumentException("accountId is required");
	}

	private static void requirePositiveAmount (Money amount) {
		if (amount == null || !amount.isGreaterThan(Money.zero())) {
			throw new IllegalArgumentException("Transaction amount must be positive");
		}
	}

	private static void requireIdempotencyKey (String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new IllegalArgumentException("idempotencyKey is required");
		}
	}
}
