package com.miqu3iasg.banking.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "banking.retry")
public record RetryProperties(

	/*
	  Maximum number of attempts (initial try + retries).
	  Default: 3
	 */
	int maxAttempts,

	/*
	  Base delay in milliseconds before the first retry.
	  Default: 50ms
	 */
	long baseDelayMs,

	/*
	  Exponential multiplier applied to the base delay on each subsequent retry.
	  Default: 2.0 → delays ~50ms, ~100ms, ~200ms
	 */
	double multiplier,

	/*
	  Maximum delay cap in milliseconds.
	  Default: 500ms (baseDelayMs * 10)
	 */
	long maxDelayMs
) {
	public RetryProperties {
		if (maxAttempts <= 0)  maxAttempts = 3;
		if (baseDelayMs <= 0)  baseDelayMs = 50L;
		if (multiplier  <= 0)  multiplier  = 2.0;
		if (maxDelayMs  <= 0)  maxDelayMs  = baseDelayMs * 10;
	}
}
