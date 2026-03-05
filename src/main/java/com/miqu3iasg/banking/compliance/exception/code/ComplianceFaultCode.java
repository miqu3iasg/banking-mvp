package com.miqu3iasg.banking.compliance.exception.code;

import com.miqu3iasg.banking.shared.exception.FaultCode;

public enum ComplianceFaultCode implements FaultCode {
	INVALID_CNPJ("BANKING_COMP_001", "Invalid CNPJ: check digits do not match.", 400),
	INVALID_CPF("BANKING_COMP_002", "Invalid CPF: check digits do not match.", 400),
	COMPLIANCE_SERVICE_UNAVAILABLE("BANKING_COMP_002", "Compliance service is currently unavailable.", 503),
	CNPJ_NOT_FOUND("BANKING_COMP_003", "CNPJ not found in compliance service", 404),
	CPF_ALL_SAME_DIGITS("BANKING_COMP_004", "CPF cannot have all digits the same", 400),
	UNKNOWN_ERROR("BANKING_COMP_999", "An unknown error occurred in the compliance service", 500);

	private final String code;
	private final String defaultMessage;
	private final int httpStatus;

	ComplianceFaultCode (String code, String defaultMessage, int httpStatus) {
		this.code = code;
		this.defaultMessage = defaultMessage;
		this.httpStatus = httpStatus;
	}

	@Override
	public String getCode () {
		return code;
	}

	@Override
	public String getDefaultMessage () {
		return defaultMessage;
	}

	@Override
	public int getHttpStatus () {
		return httpStatus;
	}
}
