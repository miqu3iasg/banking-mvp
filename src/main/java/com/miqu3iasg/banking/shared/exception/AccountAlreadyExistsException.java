package com.miqu3iasg.banking.shared.exception;

import java.util.Map;

public class AccountAlreadyExistsException extends BusinessException {

	public AccountAlreadyExistsException (String documentNumber, Throwable cause) {
		super(
			String.format("An account with document number %s already exists", documentNumber),
			AccountFaultCode.ACCOUNT_ALREADY_EXISTS,
			Map.of("documentNumber", documentNumber),
			cause
		);
	}

	public AccountAlreadyExistsException (String documentNumber) {
		super(
			String.format("An account with document number %s already exists", documentNumber),
			AccountFaultCode.ACCOUNT_ALREADY_EXISTS,
			Map.of("documentNumber", documentNumber),
			null
		);
	}
}
