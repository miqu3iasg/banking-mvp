package com.miqu3iasg.banking.shared.config;

import com.miqu3iasg.banking.pix.exception.PixAuthenticationException;
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
	private final RetryProperties props;

	@Bean
	public RetryTemplate accountLockRetryTemplate () {
		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
			props.maxAttempts(),
			Map.of(
				OptimisticLockException.class, true,
				OptimisticLockingFailureException.class, true,
				TransientDataAccessException.class, true
			),
			/* traverseCauses= */ true,
			/* defaultValue (non-listed exceptions not retried) = */ false
		);

		ExponentialRandomBackOffPolicy backOff = new ExponentialRandomBackOffPolicy();

		backOff.setInitialInterval(props.baseDelayMs());
		backOff.setMultiplier(props.multiplier());
		backOff.setMaxInterval(props.maxDelayMs());

		return RetryTemplate.builder()
			.customPolicy(retryPolicy)
			.customBackoff(backOff)
			.build();
	}

	@Bean
	public RetryTemplate efiRetryTemplate () {
		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
			props.maxAttempts(),
			Map.of(
				PixAuthenticationException.class, true
			),
			/* traverseCauses= */ true,
			/* defaultValue (non-listed exceptions not retried) = */ false
		);

		ExponentialRandomBackOffPolicy backOff = new ExponentialRandomBackOffPolicy();

		backOff.setInitialInterval(props.baseDelayMs());
		backOff.setMultiplier(props.multiplier());
		backOff.setMaxInterval(props.baseDelayMs());

		return RetryTemplate.builder()
			.customPolicy(retryPolicy)
			.customBackoff(backOff)
			.build();
	}

	@Bean
	public RetryTemplate depositLockRetryTemplate () {
		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
			props.maxAttempts(),
			Map.of(
				OptimisticLockException.class, true,
				OptimisticLockingFailureException.class, true,
				TransientDataAccessException.class, true
			),
			/* traverseCauses= */ true,
			/* defaultValue= */ false
		);

		ExponentialRandomBackOffPolicy backOff = new ExponentialRandomBackOffPolicy();
		backOff.setInitialInterval(props.baseDelayMs());
		backOff.setMultiplier(props.multiplier());
		backOff.setMaxInterval(props.maxDelayMs());

		return RetryTemplate.builder()
			.customPolicy(retryPolicy)
			.customBackoff(backOff)
			.build();
	}
}
