package com.miqu3iasg.banking.shared.exception;

import com.miqu3iasg.banking.shared.exception.code.TransactionFaultCode;

import java.util.Map;

public class InvalidCurrencyException extends BusinessException {
	private final String currencyCode;

	public InvalidCurrencyException (String currencyCode) {
		super(
			TransactionFaultCode.INVALID_CURRENCY.getDefaultMessage(),
			TransactionFaultCode.INVALID_CURRENCY,
			Map.of("currencyCode", currencyCode),
			null
		);
		this.currencyCode = currencyCode;
	}

	public InvalidCurrencyException (String currencyCode, Throwable cause) {
		super(
			TransactionFaultCode.INVALID_CURRENCY.getDefaultMessage(),
			TransactionFaultCode.INVALID_CURRENCY,
			Map.of("currencyCode", currencyCode),
			cause
		);
		this.currencyCode = currencyCode;
	}
}
