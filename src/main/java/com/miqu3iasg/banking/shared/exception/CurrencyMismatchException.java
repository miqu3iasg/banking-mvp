package com.miqu3iasg.banking.shared.exception;

import java.util.Currency;

public final class CurrencyMismatchException extends IllegalArgumentException {

	private final Currency left;
	private final Currency right;

	public CurrencyMismatchException (Currency left, Currency right) {
		super("Cannot operate on different currencies: %s vs %s"
			.formatted(left.getCurrencyCode(), right.getCurrencyCode()));
		this.left = left;
		this.right = right;
	}

	public Currency getLeft () { return left; }

	public Currency getRight () { return right; }
}
