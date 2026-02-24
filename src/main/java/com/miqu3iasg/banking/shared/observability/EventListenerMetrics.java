package com.miqu3iasg.banking.account.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class EventListenerMetrics {
	static final String EVENTS_PROCESSED = "banking.events.account.processed.total";
	static final String PROCESSING_DURATION = "banking.events.account.processing.duration.seconds";
	static final String AUDIT_FAILURES = "banking.events.account.audit.failures.total";
	static final String NOTIFICATION_FAILURES = "banking.events.account.notification.failures.total";

	static final String TAG_EVENT_TYPE = "event_type";
	static final String TAG_RESULT = "result";
	static final String TAG_FAILURE_REASON = "reason";

	static final String RESULT_SUCCESS = "success";
	static final String RESULT_FAILURE = "failure";

	private final MeterRegistry registry;

	public EventListenerMetrics (MeterRegistry registry) {
		this.registry = registry;
	}

	public void recordEventProcessed (String eventType, Runnable handler) {
		Timer timer = Timer.builder(PROCESSING_DURATION)
			.description("Time to fully process a domain event including audit and notification")
			.tag(TAG_EVENT_TYPE, eventType)
			.publishPercentileHistogram()
			.sla(Duration.ofMillis(100), Duration.ofMillis(500), Duration.ofSeconds(2))
			.register(registry);

		long start = System.nanoTime();

		try {
			handler.run();

			timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);

			incrementProcessed(eventType, RESULT_SUCCESS);

		} catch (Exception e) {
			timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);

			incrementProcessed(eventType, RESULT_FAILURE);

			throw e;
		}
	}

	public void recordAuditFailure (String eventType, String reason) {
		Counter.builder(AUDIT_FAILURES)
			.description("Number of audit write failures during event processing")
			.tag(TAG_EVENT_TYPE, eventType)
			.tag(TAG_FAILURE_REASON, reason)
			.register(registry)
			.increment();
	}

	public void recordNotificationFailure (String eventType, String reason) {
		Counter.builder(NOTIFICATION_FAILURES)
			.description("Number of notification delivery failures during event processing")
			.tag(TAG_EVENT_TYPE, eventType)
			.tag(TAG_FAILURE_REASON, reason)
			.register(registry)
			.increment();
	}

	private void incrementProcessed (String eventType, String result) {
		Counter.builder(EVENTS_PROCESSED)
			.description("Total number of account domain events processed")
			.tag(TAG_EVENT_TYPE, eventType)
			.tag(TAG_RESULT, result)
			.register(registry)
			.increment();
	}
}
