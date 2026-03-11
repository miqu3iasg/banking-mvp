package com.miqu3iasg.banking.shared.observability;

public interface RetryMetrics {
	void recordLockRetry (String action, int attempt);
}
