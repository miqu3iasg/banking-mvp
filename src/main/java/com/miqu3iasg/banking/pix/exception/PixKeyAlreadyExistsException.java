package com.miqu3iasg.banking.pix.exception;

import com.miqu3iasg.banking.shared.exception.BusinessException;

import java.util.Map;

public class PixKeyAlreadyExistsException extends BusinessException {
	public PixKeyAlreadyExistsException(String keyValue) {
		super(
			"Pix key with value '%s' already exists.".formatted(keyValue),
			PixFaultCode.PIX_KEY_ALREADY_EXISTS,
			Map.of("keyValue", keyValue),
			null
		);
	}
}
