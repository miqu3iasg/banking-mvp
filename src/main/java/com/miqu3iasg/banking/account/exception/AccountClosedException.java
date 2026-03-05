package com.miqu3iasg.banking.account.exception;

import com.miqu3iasg.banking.shared.exception.BusinessException;
import lombok.Getter;

import java.util.Map;

@Getter
public class AccountClosedException extends BusinessException {

	private final String status;

	public AccountClosedException (String accountNumber, String status) {
		super(
			String.format("Account %s is closed", accountNumber),
			AccountFaultCode.ACCOUNT_CLOSED,
			Map.of(
				"accountNumber", accountNumber,
				"status", status
			),
			null
		);
		this.status = status;
	}
}
