package com.miqu3iasg.banking.account.domain;

import lombok.Getter;

@Getter
public enum AccountType {
	CHECKING("Checking Account"),
	SAVINGS("Savings Account");

	private final String description;

	AccountType (String description) {
		this.description = description;
	}

}
