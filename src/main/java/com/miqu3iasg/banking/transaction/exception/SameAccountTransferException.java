package com.miqu3iasg.banking.transaction.exception;

import com.miqu3iasg.banking.shared.exception.BusinessException;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;

@Getter
public class SameAccountTransferException extends BusinessException {
	private final UUID accountId;

	public SameAccountTransferException (UUID accountId) {
		super(
			String.format("Cannot transfer to the same account: %s", accountId),
			TransactionFaultCode.SAME_ACCOUNT_TRANSFER,
			Map.of("accountId", accountId),
			null
		);
		this.accountId = accountId;
	}
}
