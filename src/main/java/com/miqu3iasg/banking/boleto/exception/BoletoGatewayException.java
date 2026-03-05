package com.miqu3iasg.banking.boleto.exception;

public class BoletoGatewayException extends RuntimeException {

	public BoletoGatewayException (String message) {
		super(message);
	}

	public BoletoGatewayException (String message, Throwable cause) {
		super(message, cause);
	}
}
