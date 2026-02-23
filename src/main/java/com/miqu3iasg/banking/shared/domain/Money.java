package com.miqu3iasg.banking.shared.domain;

import com.miqu3iasg.banking.shared.exception.CurrencyMismatchException;
import com.miqu3iasg.banking.shared.exception.InvalidCurrencyException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {

	public static final Currency BRL = Currency.getInstance("BRL");
	public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
	public static final int SCALE = 4;

	public Money {
		Objects.requireNonNull(amount, "amount must not be null");
		Objects.requireNonNull(currency, "currency must not be null");

		if (amount.signum() < 0) {
			throw new IllegalArgumentException(
				"Money amount must not be negative, got: " + amount.toPlainString());
		}

		amount = amount.setScale(SCALE, ROUNDING);
	}

	public static Money of (BigDecimal amount, String currencyCode) {
		return new Money(amount, parseCurrency(currencyCode));
	}

	public static Money of (BigDecimal amount, Currency currency) {
		return new Money(amount, currency);
	}

	public static Money of (String amount, String currencyCode) {
		return of(new BigDecimal(amount), currencyCode);
	}

	public static Money zero (String currencyCode) {
		return of(BigDecimal.ZERO, currencyCode);
	}

	public static Money zero (Currency currency) {
		return of(BigDecimal.ZERO, currency);
	}

	public static Money brl (BigDecimal amount) {
		return new Money(amount, BRL);
	}

	public static Money brl (String amount) {
		return brl(new BigDecimal(amount));
	}

	public Money add (Money other) {
		requireSameCurrency(other);
		return new Money(this.amount.add(other.amount), this.currency);
	}

	public Money subtract (Money other) {
		requireSameCurrency(other);
		BigDecimal result = this.amount.subtract(other.amount);

		if (result.signum() < 0) {
			throw new IllegalArgumentException(
				"Subtraction would produce a negative amount: %s - %s"
					.formatted(this.amount.toPlainString(), other.amount.toPlainString()));
		}

		return new Money(result, this.currency);
	}

	public boolean isGreaterThan (Money other) {
		requireSameCurrency(other);
		return this.amount.compareTo(other.amount) > 0;
	}

	public boolean isLessThan (Money other) {
		requireSameCurrency(other);
		return this.amount.compareTo(other.amount) < 0;
	}

	public boolean isGreaterThanOrEqualTo (Money other) {
		requireSameCurrency(other);
		return this.amount.compareTo(other.amount) >= 0;
	}

	public boolean isZero () {
		return this.amount.signum() == 0;
	}

	public boolean isPositive () {
		return this.amount.signum() > 0;
	}

	/**
	 * Parses an ISO 4217 currency code into a {@link Currency} instance.
	 *
	 * @param currencyCode the ISO 4217 currency code (e.g. {@code "USD"}, {@code "BRL"})
	 * @return the corresponding {@link Currency}
	 * @throws InvalidCurrencyException if the currency code is not recognized
	 */
	public static Currency parseCurrency (String currencyCode) {
		try {
			return Currency.getInstance(currencyCode);
		} catch (IllegalArgumentException e) {
			throw new InvalidCurrencyException(currencyCode, e);
		}
	}

	private void requireSameCurrency (Money other) {
		Objects.requireNonNull(other, "other must not be null");
		if (!this.currency.equals(other.currency)) {
			throw new CurrencyMismatchException(this.currency, other.currency);
		}
	}
}
