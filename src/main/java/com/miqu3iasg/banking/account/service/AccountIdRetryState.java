package com.miqu3iasg.banking.account.service;

import com.miqu3iasg.banking.shared.exception.TransientExceptionClassifier;
import org.springframework.retry.RetryState;

import java.util.UUID;

public class AccountIdRetryState implements RetryState {

	private final UUID accountId;

	public AccountIdRetryState (UUID accountId) {
		this.accountId = accountId;
	}

	@Override
	public Object getKey () {
		return "account-retry:" + accountId.toString();
	}

	@Override
	public boolean isForceRefresh () {
		return false;
	}

	@Override
	public boolean rollbackFor (Throwable exception) {
		return !TransientExceptionClassifier.isRetryable(exception);
	}
}
