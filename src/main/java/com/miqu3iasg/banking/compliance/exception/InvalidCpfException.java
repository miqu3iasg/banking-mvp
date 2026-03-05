package com.miqu3iasg.banking.compliance.exception;

import com.miqu3iasg.banking.shared.exception.BusinessException;
import com.miqu3iasg.banking.compliance.exception.code.ComplianceFaultCode;

import java.util.Map;

public class InvalidCpfException extends BusinessException {

	public InvalidCpfException (String cpf) {
		super(
			"Invalid CPF: check digits do not match.",
			ComplianceFaultCode.INVALID_CPF,
			Map.of("cpf", cpf),
			null
		);
	}

	public InvalidCpfException (String cpf, String reason) {
		super(
			"Invalid CPF: " + reason,
			ComplianceFaultCode.INVALID_CPF,
			Map.of("cpf", cpf, "reason", reason),
			null
		);
	}
}
