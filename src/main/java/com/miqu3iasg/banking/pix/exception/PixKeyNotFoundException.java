package com.miqu3iasg.banking.pix.exception;

import com.miqu3iasg.banking.shared.exception.BusinessException;

import java.util.Map;

public class PixKeyNotFoundException extends BusinessException {
	public PixKeyNotFoundException(String keyValue) {
		super(
			PixFaultCode.PIX_KEY_NOT_FOUND.getDefaultMessage(),
			PixFaultCode.PIX_KEY_NOT_FOUND,
			Map.of("keyValue", keyValue),
			null
		);
	}
}
