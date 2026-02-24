package com.miqu3iasg.banking.shared.exception;

import com.miqu3iasg.banking.shared.exception.code.AccountFaultCode;
import lombok.Getter;

import java.util.Map;

@Getter
public class AccountBlockedException extends BusinessException {

	private final String status;

	public AccountBlockedException (String accountNumber, String status) {
		super(
			String.format("Account %s is blocked", accountNumber),
			AccountFaultCode.ACCOUNT_BLOCKED,
			Map.of(
				"accountNumber", accountNumber,
				"status", status
			),
			null
		);
		this.status = status;
	}
}
