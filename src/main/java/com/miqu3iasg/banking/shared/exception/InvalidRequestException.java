package com.miqu3iasg.banking.shared.exception;

public class InvalidRequestException extends BusinessException {
	public InvalidRequestException (String message, FaultCode faultCode) {
		super(message, faultCode, null, null);
	}

	public InvalidRequestException (FaultCode faultCode) {
		super(faultCode.getDefaultMessage(), faultCode, null, null);
	}

	public InvalidRequestException (String message, FaultCode faultCode, Throwable cause) {
		super(message, faultCode, null, cause);
	}
}
