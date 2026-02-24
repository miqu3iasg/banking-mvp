package com.miqu3iasg.banking.shared.exception;

import com.miqu3iasg.banking.shared.exception.code.CustomerFaultCode;

import java.util.Collections;

public class InvalidDocumentException extends BusinessException {

	public InvalidDocumentException (String message, Throwable cause) {
		super(message, CustomerFaultCode.CUSTOMER_INVALID_DOCUMENT, Collections.emptyMap(), cause);
	}

	public InvalidDocumentException (String message) {
		super(message, CustomerFaultCode.CUSTOMER_INVALID_DOCUMENT, Collections.emptyMap(), null);
	}
}
