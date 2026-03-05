package com.miqu3iasg.banking.transaction.exception;

import com.miqu3iasg.banking.shared.exception.FaultCode;

/**
 * Fault codes for the Transaction bounded context.
 *
 * <p>Code range: {@code BANKING_TXN_001} – {@code BANKING_TXN_099}</p>
 *
 * <p>Usage:
 * <pre>{@code
 * throw BusinessException.of(TransactionFaultCode.DUPLICATE_TRANSACTION,
 *     Map.of("idempotencyKey", key, "originalTxnId", original));
 * }</pre>
 * </p>
 */
public enum TransactionFaultCode implements FaultCode {

	TRANSACTION_NOT_FOUND("BANKING_TXN_001", "Transaction not found", 404),

	INVALID_TRANSACTION_AMOUNT("BANKING_TXN_010", "Transaction amount must be greater than zero", 400),
	INVALID_CURRENCY("BANKING_TXN_011", "Currency code is invalid or unsupported", 400),

	TRANSACTION_LIMIT_EXCEEDED("BANKING_TXN_020", "Transaction exceeds allowed limit", 422),
	DAILY_LIMIT_EXCEEDED("BANKING_TXN_021", "Daily transaction limit has been reached", 422),
	DUPLICATE_TRANSACTION("BANKING_TXN_022", "Duplicate transaction detected", 409),

	SAME_ACCOUNT_TRANSFER("BANKING_TXN_030", "Source and destination accounts must differ", 400),
	CURRENCY_MISMATCH("BANKING_TXN_031", "Account currencies do not match", 422),

	TRANSACTION_ALREADY_REVERSED("BANKING_TXN_040", "Transaction has already been reversed", 409),
	TRANSACTION_NOT_REVERSIBLE("BANKING_TXN_041", "Transaction type does not support reversal", 422),
	TRANSACTION_EXPIRED("BANKING_TXN_042", "Transaction window has expired", 422);

	private final String code;
	private final String defaultMessage;
	private final int httpStatus;

	TransactionFaultCode (String code, String defaultMessage, int httpStatus) {
		this.code = code;
		this.defaultMessage = defaultMessage;
		this.httpStatus = httpStatus;
	}

	@Override
	public String getCode () { return code; }

	@Override
	public String getDefaultMessage () { return defaultMessage; }

	@Override
	public int getHttpStatus () { return httpStatus; }

	@Override
	public String toString () {
		return String.format("%s(%s)", name(), code);
	}
}
