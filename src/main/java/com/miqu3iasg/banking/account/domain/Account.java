package com.miqu3iasg.banking.account.domain;

import com.miqu3iasg.banking.shared.domain.AuditableEntity;
import com.miqu3iasg.banking.shared.domain.Money;
import com.miqu3iasg.banking.shared.exception.AccountBlockedException;
import com.miqu3iasg.banking.shared.exception.AccountFaultCode;
import com.miqu3iasg.banking.shared.exception.BusinessException;
import com.miqu3iasg.banking.shared.exception.InsufficientFundsException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	name = "accounts",
	indexes = {
		@Index(name = "idx_accounts_document", columnList = "document_number"),
		@Index(name = "idx_accounts_status", columnList = "status"),
		@Index(name = "idx_accounts_number", columnList = "account_number", unique = true)
	}
)
public class Account extends AuditableEntity {

	@Column(name = "account_number", nullable = false, unique = true, length = 8)
	private String accountNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountStatus status;

	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "amount", column = @Column(name = "balance", nullable = false, precision = 19, scale = 4)),
		@AttributeOverride(name = "currency", column = @Column(name = "currency_code", nullable = false, length = 3))
	})
	private Money balance;

	@Column(name = "holder_name", nullable = false)
	private String holderName;

	@Column(name = "document_number", nullable = false, length = 18)
	private String documentNumber;

	@Column(name = "email", nullable = false)
	private String email;

	public static Account open (
		String accountNumber,
		AccountType type,
		String holderName,
		String documentNumber,
		String email
	) {
		validateOpeningArgs(accountNumber, type, holderName, documentNumber, email);

		Account account = new Account();
		account.accountNumber = accountNumber;
		account.type = type;
		account.holderName = holderName;
		account.documentNumber = documentNumber;
		account.email = email;
		account.status = AccountStatus.ACTIVE;
		account.balance = Money.zero();
		return account;
	}

	public void credit (Money amount) {
		requireActive();
		requirePositiveAmount(amount);
		this.balance = this.balance.add(amount);
	}

	public void debit (Money amount) {
		requireActive();
		requirePositiveAmount(amount);
		requireSufficientFunds(amount);
		this.balance = this.balance.subtract(amount);
	}

	public void block () {
		requireNotClosed();
		this.status = AccountStatus.BLOCKED;
	}

	public void unblock () {
		requireBlocked();
		this.status = AccountStatus.ACTIVE;
	}

	public void close () {
		requireActive();
		requireZeroBalance();
		this.status = AccountStatus.CLOSED;
	}

	public boolean isActive () {
		return this.status == AccountStatus.ACTIVE;
	}

	public boolean isClosed () {
		return this.status == AccountStatus.CLOSED;
	}

	public boolean isBlocked () {
		return this.status == AccountStatus.BLOCKED;
	}

	private void requireActive () {
		if (!isActive()) {
			throw new AccountBlockedException(accountNumber, status.name());
		}
	}

	private void requireNotClosed () {
		if (isClosed()) {
			throw BusinessException.of(
				AccountFaultCode.ACCOUNT_CLOSED,
				Map.of("accountNumber", accountNumber, "currentStatus", status)
			);
		}
	}

	private void requireBlocked () {
		if (!isBlocked()) {
			throw BusinessException.of(
				AccountFaultCode.ACCOUNT_NOT_BLOCKED,
				Map.of("accountNumber", accountNumber, "currentStatus", status)
			);
		}
	}

	private void requireSufficientFunds (Money required) {
		if (!this.balance.isGreaterThanOrEqual(required)) {
			throw new InsufficientFundsException(getId().toString(), required, balance);
		}
	}

	private void requireZeroBalance () {
		if (this.balance.isGreaterThan(Money.zero())) {
			throw BusinessException.of(
				AccountFaultCode.ACCOUNT_HAS_POSITIVE_BALANCE,
				Map.of("accountNumber", accountNumber, "balance", balance.amount())
			);
		}
	}

	private static void requirePositiveAmount (Money amount) {
		if (amount == null || !amount.isGreaterThan(Money.zero())) {
			throw new IllegalArgumentException("Transaction amount must be positive");
		}
	}

	private static void validateOpeningArgs (
		String accountNumber,
		AccountType type,
		String holderName,
		String documentNumber,
		String email
	) {
		if (accountNumber == null || accountNumber.isBlank())
			throw new IllegalArgumentException("accountNumber is required");
		if (type == null) throw new IllegalArgumentException("type is required");
		if (holderName == null || holderName.isBlank())
			throw new IllegalArgumentException("holderName is required");
		if (documentNumber == null || documentNumber.isBlank())
			throw new IllegalArgumentException("documentNumber is required");
		if (email == null || email.isBlank())
			throw new IllegalArgumentException("email is required");
	}
}
