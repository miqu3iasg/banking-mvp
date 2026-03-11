package com.miqu3iasg.banking.transaction.listener;

import com.miqu3iasg.banking.shared.exception.TransientExceptionClassifier;
import com.miqu3iasg.banking.shared.observability.RetryMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

@Slf4j
@RequiredArgsConstructor
public class TransactionRetryListener implements RetryListener {

	private final RetryMetrics metrics;
	private final int maxAttempts;

	@Override
	public <T, E extends Throwable> void onError (
		RetryContext context,
		RetryCallback<T, E> callback,
		Throwable t
	) {
		int attempt = context.getRetryCount();
		String action = (String) context.getAttribute("action");
		String accountId = (String) context.getAttribute("accountId");

		if (TransientExceptionClassifier.isRetryable(t)) {
			log.warn("Optimistic-lock conflict: accountId=[{}] action=[{}] (attempt {}/{}), retrying…",
				accountId,
				action != null ? action : "-",
				attempt + 1,
				maxAttempts,
				t);

			metrics.recordLockRetry(action != null ? action : "-", attempt + 1);
		} else {
			log.error("Non-retryable failure: accountId=[{}] action=[{}] (attempt {})",
				accountId,
				action != null ? action : "-",
				attempt + 1,
				t);
		}
	}
}
