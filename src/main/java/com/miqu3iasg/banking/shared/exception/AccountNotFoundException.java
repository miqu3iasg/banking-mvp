package com.miqu3iasg.banking.shared.exception;

import com.miqu3iasg.banking.account.exception.AccountFaultCode;

import java.util.Map;
import java.util.UUID;

public class AccountNotFoundException extends BusinessException {
	public AccountNotFoundException (UUID accountId) {
		super(
			String.format("Account not found: %s", accountId),
			AccountFaultCode.ACCOUNT_NOT_FOUND,
			Map.of("accountId", accountId),
			null
		);
	}

	public AccountNotFoundException (UUID accountId, Throwable cause) {
		super(
			String.format("Account not found: %s", accountId),
			AccountFaultCode.ACCOUNT_NOT_FOUND,
			Map.of("accountId", accountId),
			cause
		);
	}

}
