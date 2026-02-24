package com.miqu3iasg.banking.shared.exception.code;

/**
 * Fault codes for the Customer bounded context.
 *
 * <p>Code range: {@code BANKING_CST_001} – {@code BANKING_CST_099}</p>
 *
 * <p>Usage:
 * <pre>{@code
 * throw BusinessException.of(CustomerFaultCode.UNDERAGE_CUSTOMER,
 *     Map.of("customerId", id, "age", age, "minimumAge", 18));
 * }</pre>
 * </p>
 */
public enum CustomerFaultCode implements FaultCode {

	CUSTOMER_NOT_FOUND("BANKING_CST_001", "Customer not found", 404),

	CUSTOMER_INVALID_INPUT("BANKING_CST_002", "Customer input data is invalid", 422),

	CUSTOMER_ALREADY_EXISTS("BANKING_CST_010", "Customer already registered with this document", 409),
	CUSTOMER_BLACKLISTED("BANKING_CST_021", "Customer is blacklisted and cannot open accounts", 403),
	CUSTOMER_PENDING_KYC("BANKING_CST_022", "Customer identity verification (KYC) is pending", 403),
	CUSTOMER_KYC_REJECTED("BANKING_CST_023", "Customer identity verification (KYC) was rejected", 403),

	CUSTOMER_INACTIVE("BANKING_CST_030", "Customer account is inactive", 422),
	CUSTOMER_SUSPENDED("BANKING_CST_031", "Customer has been suspended", 403),

	CUSTOMER_INVALID_DOCUMENT("BANKING_CST_040", "Customer document number is invalid", 422),
	UNDERAGE_CUSTOMER("BANKING_CST_041", "Customer does not meet the minimum age requirement to open an account", 422),
	CUSTOMER_INVALID_EMAIL("BANKING_CST_042", "Customer email address is invalid", 422);

	private final String code;
	private final String defaultMessage;
	private final int httpStatus;

	CustomerFaultCode (String code, String defaultMessage, int httpStatus) {
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
