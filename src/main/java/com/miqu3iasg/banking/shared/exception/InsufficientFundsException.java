package com.miqu3iasg.banking.shared.exception;

import com.miqu3iasg.banking.shared.domain.Money;
import com.miqu3iasg.banking.shared.exception.code.AccountFaultCode;
import lombok.Getter;

import java.util.Map;

@Getter
public class InsufficientFundsException extends BusinessException {
	private final String accountId;
	private final Money available;
	private final Money required;

	public InsufficientFundsException (String accountId, Money required, Money available) {
		super(
			AccountFaultCode.INSUFFICIENT_FUNDS.getDefaultMessage(),
			AccountFaultCode.INSUFFICIENT_FUNDS,
			Map.of(
				"accountId", accountId,
				"required", required,
				"available", available,
				"shortfall", required.subtract(available)
			),
			null
		);
		this.accountId = accountId;
		this.required = required;
		this.available = available;
	}
}
