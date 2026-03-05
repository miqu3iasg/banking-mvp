package com.miqu3iasg.banking.pix.exception;

import com.miqu3iasg.banking.shared.exception.FaultCode;

public enum PixFaultCode implements FaultCode {
	PIX_CHARGE_NOT_FOUND("BANKING_PIX_001", "PIX charge not found", 404),
	PIX_KEY_NOT_FOUND("BANKING_PIX_002", "PIX key not found", 404),

	PIX_KEY_ALREADY_EXISTS("BANKING_PIX_010", "PIX key already exists", 409),
	PIX_CHARGE_ALREADY_EXISTS("BANKING_PIX_011", "PIX charge already exists for this account and amount", 409),

	INVALID_PIX_STATE_TRANSITION("BANKING_PIX_020", "Invalid state transition for PIX charge", 422),
	PIX_CHARGE_EXPIRED("BANKING_PIX_021", "PIX charge has expired", 422),

	PIX_GATEWAY_UNAVAILABLE("BANKING_PIX_030", "PIX gateway is currently unavailable", 503);

	private final String code;
	private final String defaultMessage;
	private final int httpStatus;

	PixFaultCode (String code, String defaultMessage, int httpStatus) {
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
