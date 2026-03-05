package com.miqu3iasg.banking.compliance.exception;

import com.miqu3iasg.banking.compliance.exception.code.ComplianceFaultCode;
import com.miqu3iasg.banking.shared.exception.BusinessException;

import java.util.Map;

public class ComplianceServiceUnavailableException extends BusinessException {
	public ComplianceServiceUnavailableException (String message) {
		super(
			message,
			ComplianceFaultCode.COMPLIANCE_SERVICE_UNAVAILABLE,
			Map.of(),
			null
		);
	}

	public ComplianceServiceUnavailableException (String message, Throwable cause) {
		super(
			message,
			ComplianceFaultCode.COMPLIANCE_SERVICE_UNAVAILABLE,
			Map.of(),
			cause
		);
	}
}
