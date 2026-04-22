package com.miqu3iasg.banking.boleto.metrics;

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
public class BoletoMetrics {

	private static final String BOLETO_ISSUED = "banking.boleto.issued.total";
	private static final String PAYMENT_RECEIVED = "banking.boleto.payment.received.total";
	private static final String WEBHOOK_REJECTED = "banking.boleto.webhook.rejected.total";
	private static final String BOLETOS_EXPIRED = "banking.boleto.expired.total";
	private static final String GATEWAY_ERRORS = "banking.boleto.gateway.errors.total";
	private static final String GATEWAY_RETRIES = "banking.boleto.gateway.retries.total";
	private static final String GATEWAY_DURATION = "banking.boleto.gateway.call.duration.seconds";
	private static final String ISSUANCE_DURATION = "banking.boleto.issuance.duration.seconds";

	private static final String TAG_OPERATION = "operation";
	private static final String TAG_RESULT = "result";
	private static final String TAG_REASON = "reason";
	private static final String TAG_ATTEMPT = "attempt";

	private final MeterRegistry registry;
	private final ConcurrentHashMap<String, Timer> gatewayTimers = new ConcurrentHashMap<>();
	private final Timer issuanceTimer;

	public BoletoMetrics (MeterRegistry registry) {
		this.registry = registry;
		this.issuanceTimer = Timer.builder(ISSUANCE_DURATION)
			.description("End-to-end latency of boleto issuance gateway call")
			.publishPercentileHistogram()
			.sla(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofSeconds(5))
			.register(registry);
	}

	public <T> T timeGatewayCall (String operation, Supplier<T> supplier) {
		var timer = gatewayTimers.computeIfAbsent(operation, op ->
			Timer.builder(GATEWAY_DURATION)
				.description("Latency of individual Efí Bank Cobranças API calls")
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

	public <T> T timeBoletoIssuance (Supplier<T> supplier) {
		return issuanceTimer.record(supplier);
	}

	public void recordBoletoIssued () {
		Counter.builder(BOLETO_ISSUED)
			.description("Total boletos successfully issued at the provider")
			.tag(TAG_RESULT, "success")
			.register(registry)
			.increment();
	}

	public void recordPaymentReceived () {
		Counter.builder(PAYMENT_RECEIVED)
			.description("Boleto webhook payments successfully processed")
			.register(registry)
			.increment();
	}

	public void recordWebhookRejected (String reason) {
		Counter.builder(WEBHOOK_REJECTED)
			.description("Boleto webhook calls rejected before processing")
			.tag(TAG_REASON, reason)
			.register(registry)
			.increment();
	}

	public void recordBoletosExpired (int count) {
		if (count > 0) {
			Counter.builder(BOLETOS_EXPIRED)
				.description("Total boletos expired by the scheduled job")
				.register(registry)
				.increment(count);
		}
	}

	public void recordGatewayError (String operation, String errorClass) {
		Counter.builder(GATEWAY_ERRORS)
			.description("Total errors in the boleto gateway layer")
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
}
