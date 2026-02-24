package com.miqu3iasg.banking.shared.exception;

public class AccountNumberGenerationException extends RuntimeException {

	private static final String MESSAGE_TEMPLATE =
		"Unable to generate a unique account number after %d attempts. " +
			"The database sequence is likely corrupted or out of sync — immediate investigation required.";

	public AccountNumberGenerationException (int attempts) {
		super(String.format(MESSAGE_TEMPLATE, attempts));
	}
}
