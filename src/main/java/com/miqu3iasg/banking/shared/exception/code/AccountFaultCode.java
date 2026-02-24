package com.miqu3iasg.banking.shared.exception;

/**
 * Fault codes for the Account bounded context.
 *
 * <p>Code range: {@code BANKING_ACC_001} – {@code BANKING_ACC_099}</p>
 *
 * <p>Usage:
 * <pre>{@code
 * throw BusinessException.of(AccountFaultCode.ACCOUNT_NOT_FOUND,
 *     Map.of("accountId", id));
 * }</pre>
 * </p>
 */
public enum AccountFaultCode implements FaultCode {
	ACCOUNT_NOT_FOUND("BANKING_ACC_001", "Account not found", 404),
	ACCOUNT_HOLDER_NOT_FOUND("BANKING_ACC_002", "Account holder not found", 404),

	ACCOUNT_ALREADY_EXISTS("BANKING_ACC_010", "Account already exists for this customer", 409),

	ACCOUNT_INACTIVE("BANKING_ACC_020", "Account is inactive", 422),
	ACCOUNT_BLOCKED("BANKING_ACC_021", "Account is blocked due to suspicious activity", 403),
	ACCOUNT_NOT_BLOCKED("BANKING_ACC_022", "Account is not blocked", 422),
	ACCOUNT_FROZEN("BANKING_ACC_023", "Account is frozen by court order", 403),
	ACCOUNT_CLOSED("BANKING_ACC_024", "Account has been permanently closed", 422),

	INSUFFICIENT_FUNDS("BANKING_ACC_030", "Insufficient funds to complete the operation", 422),
	ACCOUNT_HAS_POSITIVE_BALANCE("BANKING_ACC_031", "Account has a positive balance and cannot be closed", 422),
	BALANCE_BELOW_MINIMUM("BANKING_ACC_032", "Balance would fall below required minimum", 422);

	private final String code;
	private final String defaultMessage;
	private final int httpStatus;

	AccountFaultCode (String code, String defaultMessage, int httpStatus) {
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
