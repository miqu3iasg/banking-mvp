package com.miqu3iasg.banking.shared.config;

import com.miqu3iasg.banking.account.service.AccountMetrics;
import com.miqu3iasg.banking.pix.exception.BoletoAuthenticationException;
import com.miqu3iasg.banking.pix.exception.PixAuthenticationException;
import com.miqu3iasg.banking.shared.observability.RetryMetrics;
import com.miqu3iasg.banking.transaction.listener.TransactionRetryListener;
import com.miqu3iasg.banking.transaction.service.TransactionMetrics;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RetryProperties.class)
public class RetryConfig {

	private static final Map<Class<? extends Throwable>, Boolean> OPTIMISTIC_LOCK_EXCEPTIONS = Map.of(
		OptimisticLockException.class, true,
		OptimisticLockingFailureException.class, true,
		TransientDataAccessException.class, true
	);

	private final RetryProperties props;
	private final TransactionMetrics transactionMetrics;
	private final AccountMetrics accountMetrics;

	@Bean("efiPixRetryTemplate")
	public RetryTemplate efiPixRetryTemplate () {
		return buildTemplate(Map.of(PixAuthenticationException.class, true), null);
	}

	@Bean("efiBoletoRetryTemplate")
	public RetryTemplate efiBoletoRetryTemplate () {
		return buildTemplate(Map.of(BoletoAuthenticationException.class, true), null);
	}

	@Bean("accountLockRetryTemplate")
	public RetryTemplate accountLockRetryTemplate () {
		return buildLockRetryTemplate(accountMetrics);
	}

	@Bean("depositLockRetryTemplate")
	public RetryTemplate depositLockRetryTemplate () {
		return buildLockRetryTemplate(transactionMetrics);
	}

	@Bean("withdrawalLockRetryTemplate")
	public RetryTemplate withdrawalLockRetryTemplate () {
		return buildLockRetryTemplate(transactionMetrics);
	}

	@Bean("transferLockRetryTemplate")
	public RetryTemplate transferLockRetryTemplate () {
		return buildLockRetryTemplate(transactionMetrics);
	}

	private RetryTemplate buildLockRetryTemplate (RetryMetrics retryMetrics) {
		return buildTemplate(OPTIMISTIC_LOCK_EXCEPTIONS, retryMetrics);
	}

	private RetryTemplate buildTemplate (
		Map<Class<? extends Throwable>, Boolean> retryableExceptions,
		RetryMetrics metrics
	) {
		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
			props.maxAttempts(),
			retryableExceptions,
			/* traverseCauses= */ true,
			/* defaultValue= */ false
		);

		ExponentialRandomBackOffPolicy backOff = new ExponentialRandomBackOffPolicy();

		backOff.setInitialInterval(props.baseDelayMs());
		backOff.setMultiplier(props.multiplier());
		backOff.setMaxInterval(props.maxDelayMs());

		RetryTemplate template = RetryTemplate.builder()
			.customPolicy(retryPolicy)
			.customBackoff(backOff)
			.build();


		if (metrics != null) {
			template.registerListener(new TransactionRetryListener(metrics, props.maxAttempts()));
		}

		return template;
	}
}
