package com.miqu3iasg.banking.account.api.dto;

import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.domain.AccountStatus;
import com.miqu3iasg.banking.account.domain.AccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AccountResponse(
	UUID id,
	String accountNumber,
	AccountType type,
	AccountStatus status,
	String holderName,
	String documentNumber,
	String email,
	BigDecimal balance,
	String currency,
	Instant createdAt
) {

	public static AccountResponse from (Account account) {
		Objects.requireNonNull(account, "Account must not be null");

		return new AccountResponse(
			account.getId(),
			account.getAccountNumber(),
			account.getType(),
			account.getStatus(),
			account.getHolderName(),
			account.getDocumentNumber(),
			account.getEmail(),
			account.getBalance().amount(),
			account.getBalance().currency().getCurrencyCode(),
			account.getCreatedAt()
		);
	}
}
