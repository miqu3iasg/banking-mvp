package com.miqu3iasg.banking.pix.exception;

import com.miqu3iasg.banking.boleto.exception.BoletoGatewayException;

public class BoletoAuthenticationException extends BoletoGatewayException {
	public BoletoAuthenticationException (String message) {
		super(message);
	}
}
