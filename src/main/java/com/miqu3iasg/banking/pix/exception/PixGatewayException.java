package com.miqu3iasg.banking.pix.exception;

import com.miqu3iasg.banking.shared.exception.BusinessException;

import java.util.Map;

public class PixGatewayException extends BusinessException {

	public PixGatewayException (String detail) {
		super(
			PixFaultCode.PIX_GATEWAY_UNAVAILABLE.getDefaultMessage(),
			PixFaultCode.PIX_GATEWAY_UNAVAILABLE,
			Map.of("detail", detail),
			null
		);
	}

	public PixGatewayException (String detail, Throwable cause) {
		super(
			PixFaultCode.PIX_GATEWAY_UNAVAILABLE.getDefaultMessage(),
			PixFaultCode.PIX_GATEWAY_UNAVAILABLE,
			Map.of("detail", detail),
			cause
		);
	}
}
