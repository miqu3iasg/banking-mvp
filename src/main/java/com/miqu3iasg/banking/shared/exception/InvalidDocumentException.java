package com.miqu3iasg.banking.shared.exception;

public class InvalidDocumentException extends BusinessException {
	public InvalidDocumentException (String message, Throwable cause) {
		super(message, CustomerFaultCode.CUSTOMER_INVALID_DOCUMENT, null, cause);
	}

	public InvalidDocumentException (String message) {
		super(message, CustomerFaultCode.CUSTOMER_INVALID_DOCUMENT, null, null);
	}
}
