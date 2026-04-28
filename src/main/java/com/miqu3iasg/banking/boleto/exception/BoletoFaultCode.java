package com.miqu3iasg.banking.boleto.exception;

import com.miqu3iasg.banking.shared.exception.FaultCode;
import org.springframework.http.HttpStatus;

public enum BoletoFaultCode implements FaultCode {
	// Standardized fault codes following same pattern as PIX
	BOLETO_NOT_FOUND("BANKING_BOLETO_001", "Boleto not found", HttpStatus.NOT_FOUND.value()),
	BOLETO_GATEWAY_ERROR("BANKING_BOLETO_002", "Error communicating with boleto provider", HttpStatus.BAD_GATEWAY.value()),
	INVALID_BOLETO_STATE_TRANSITION("BANKING_BOLETO_003", "Invalid boleto status transition", HttpStatus.CONFLICT.value()),
	BOLETO_ALREADY_PAID("BANKING_BOLETO_004", "Boleto has already been paid", HttpStatus.CONFLICT.value()),
	BOLETO_ALREADY_EXPIRED("BANKING_BOLETO_005", "Boleto has already expired", HttpStatus.CONFLICT.value());

	private final String code;
	private final String defaultMessage;
	private final int httpStatus;

	BoletoFaultCode (String code, String defaultMessage, int httpStatus) {
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

	@Override
	public String toString () {
		return String.format("%s(%s)", name(), code);
	}
}
