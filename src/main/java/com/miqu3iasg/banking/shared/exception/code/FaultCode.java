package com.miqu3iasg.banking.shared.exception.code;

public interface FaultCode {
	/**
	 * Machine-readable error identifier.
	 * Format: {@code BANKING_<DOMAIN_PREFIX>_<SEQUENCE>}
	 * Example: {@code BANKING_ACC_001}
	 */
	String getCode ();

	String getDefaultMessage ();

	int getHttpStatus ();
}
