package com.miqu3iasg.banking.shared.exception;

import com.miqu3iasg.banking.shared.exception.code.TransactionFaultCode;

import java.util.Currency;
import java.util.Map;

public final class CurrencyMismatchException extends BusinessException {

	public CurrencyMismatchException (Currency left, Currency right) {
		super(
			"Cannot operate on different currencies: %s vs %s"
				.formatted(left.getCurrencyCode(), right.getCurrencyCode()),
			TransactionFaultCode.CURRENCY_MISMATCH,
			Map.of(
				"leftCurrency", left.getCurrencyCode(),
				"rightCurrency", right.getCurrencyCode()
			),
			null
		);
	}
}
