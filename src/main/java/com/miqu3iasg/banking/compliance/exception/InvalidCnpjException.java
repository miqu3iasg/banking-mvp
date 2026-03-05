package com.miqu3iasg.banking.compliance.exception;

import com.miqu3iasg.banking.shared.exception.BusinessException;
import com.miqu3iasg.banking.compliance.exception.code.ComplianceFaultCode;

import java.util.Map;

public class InvalidCnpjException extends BusinessException {

	public InvalidCnpjException (String cnpj) {
		super(
			"Invalid CNPJ: check digits do not match.",
			ComplianceFaultCode.INVALID_CNPJ,
			Map.of("cnpj", cnpj),
			null
		);
	}

	public InvalidCnpjException (String cnpj, String reason) {
		super(
			"Invalid CNPJ: " + reason,
			ComplianceFaultCode.INVALID_CNPJ,
			Map.of("cnpj", cnpj, "reason", reason),
			null
		);
	}
}
