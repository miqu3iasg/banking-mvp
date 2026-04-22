package com.miqu3iasg.banking.pix.metrics;


import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@ConditionalOnProperty(name = "efi.webclient.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class PixMetrics {

	private static final String CHARGE_CREATED = "banking.pix.charge.created.total";
	private static final String PAYMENT_RECEIVED = "banking.pix.payment.received.total";
	private static final String WEBHOOK_REJECTED = "banking.pix.webhook.rejected.total";
	private static final String CHARGES_EXPIRED = "banking.pix.charges.expired.total";
	private static final String GATEWAY_ERRORS = "banking.pix.errors.total";
	private static final String GATEWAY_RETRIES = "banking.pix.gateway.retries.total";
	private static final String CHARGE_DURATION = "banking.pix.charge.creation.duration.seconds";
	private static final String GATEWAY_DURATION = "banking.pix.gateway.call.duration.seconds";
	private static final String EVP_KEY_CREATED = "banking.pix.evp.key.created.total";
	private static final String EVP_KEY_DELETED = "banking.pix.evp.key.deleted.total";
	private static final String EVP_KEY_LISTED = "banking.pix.evp.key.listed.total";

	private static final String TAG_OPERATION = "operation";
	private static final String TAG_RESULT = "result";
	private static final String TAG_REASON = "reason";
	private static final String TAG_ATTEMPT = "attempt";

	private final MeterRegistry registry;

	private final ConcurrentHashMap<String, Timer> gatewayTimers = new ConcurrentHashMap<>();

	public PixMetrics (MeterRegistry registry) {
		this.registry = registry;
	}

	public <T> T timeChargeCreation (Supplier<T> operation) {
		var timer = Timer.builder(CHARGE_DURATION)
			.description("End-to-end latency of PIX charge creation including gateway call")
			.publishPercentileHistogram()
			.sla(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(3))
			.register(registry);

		try {
			return timer.record(operation);
		} catch (Exception e) {
			recordGatewayError("charge_creation", e.getClass().getSimpleName());
			throw e;
		}
	}

	/**
	 * Wraps individual Efí Bank API calls.
	 * Uses ConcurrentHashMap to avoid Timer builder allocation on every call.
	 *
	 * @param operation name used as a tag, e.g. "createCharge", "getCharge", "cancelCharge"
	 */
	public <T> T timeGatewayCall (String operation, Supplier<T> supplier) {
		var timer = gatewayTimers.computeIfAbsent(operation, op ->
			Timer.builder(GATEWAY_DURATION)
				.description("Latency of individual Efí Bank API calls")
				.tag(TAG_OPERATION, op)
				.publishPercentileHistogram()
				.sla(Duration.ofMillis(200), Duration.ofMillis(500), Duration.ofSeconds(2))
				.register(registry)
		);

		try {
			return timer.record(supplier);
		} catch (Exception e) {
			recordGatewayError(operation, e.getClass().getSimpleName());
			throw e;
		}
	}

	public void recordChargeCreated () {
		Counter.builder(CHARGE_CREATED)
			.description("Total PIX charges successfully created at the provider")
			.tag(TAG_RESULT, "success")
			.register(registry)
			.increment();
	}

	public void recordPaymentReceived () {
		Counter.builder(PAYMENT_RECEIVED)
			.description("PIX webhook payments successfully processed")
			.register(registry)
			.increment();
	}

	public void recordWebhookRejected (String reason) {
		Counter.builder(WEBHOOK_REJECTED)
			.description("Webhook calls rejected before processing")
			.tag(TAG_REASON, reason)
			.register(registry)
			.increment();
	}

	public void recordChargesExpired (int count) {
		if (count > 0) {
			Counter.builder(CHARGES_EXPIRED)
				.description("Total PIX charges expired by the scheduled job")
				.register(registry)
				.increment(count);
		}
	}

	public void recordGatewayError (String operation, String errorClass) {
		Counter.builder(GATEWAY_ERRORS)
			.description("Total errors in the PIX gateway layer")
			.tag(TAG_OPERATION, operation)
			.tag("error_class", errorClass)
			.register(registry)
			.increment();
	}

	public void recordGatewayRetry (String operation, int attempt) {
		Counter.builder(GATEWAY_RETRIES)
			.description("Total retry attempts on transient Efí Bank errors")
			.tag(TAG_OPERATION, operation)
			.tag(TAG_ATTEMPT, String.valueOf(attempt))
			.register(registry)
			.increment();
	}

	public void recordEvpKeyCreated () {
		Counter.builder(EVP_KEY_CREATED)
			.description("Total EVP (random) PIX keys successfully created at Efí Bank")
			.tag(TAG_RESULT, "success")
			.register(registry)
			.increment();
	}

	public void recordEvpKeyDeleted () {
		Counter.builder(EVP_KEY_DELETED)
			.description("Total EVP (random) PIX keys successfully deleted at Efí Bank")
			.tag(TAG_RESULT, "success")
			.register(registry)
			.increment();
	}

	public void recordEvpKeyListed () {
		Counter.builder(EVP_KEY_LISTED)
			.description("Total EVP (random) PIX key list calls successfully completed")
			.tag(TAG_RESULT, "success")
			.register(registry)
			.increment();
	}
}
