/*package com.miqu3iasg.banking.shared.config;

import com.miqu3iasg.banking.pix.exception.PixGatewayException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RetryProperties.class)
public class EfiRetryConfig {
	private final RetryProperties props;

	@Bean
	public RetryTemplate efiRetryTemplate() {
		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
			props.maxAttempts(),
			Map.of(
				PixGatewayException.class, true
			),
			*//* traverseCauses= *//* true,
			*//* defaultValue (non-listed exceptions not retried) = *//* false
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
}*/
