package com.miqu3iasg.banking.shared.exception;

import com.miqu3iasg.banking.shared.exception.code.FaultCode;

import java.util.Collections;

public class InvalidRequestException extends BusinessException {

	public InvalidRequestException (String message, FaultCode faultCode) {
		super(message, faultCode, Collections.emptyMap(), null);
	}

	public InvalidRequestException (FaultCode faultCode) {
		super(faultCode.getDefaultMessage(), faultCode, Collections.emptyMap(), null);
	}

	public InvalidRequestException (
		String message,
		FaultCode faultCode,
		Throwable cause
	) {
		super(message, faultCode, Collections.emptyMap(), cause);
	}
}
