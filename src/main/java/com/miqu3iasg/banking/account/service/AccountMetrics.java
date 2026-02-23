package com.miqu3iasg.banking.account.service;

import com.miqu3iasg.banking.account.domain.AccountType;
import com.miqu3iasg.banking.shared.exception.BusinessException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class AccountMetrics {

	private static final String ACCOUNTS_OPENED = "banking.accounts.opened.total";
	private static final String STATUS_TRANSITIONS = "banking.accounts.status.transitions.total";
	private static final String OPEN_DURATION = "banking.accounts.open.duration.seconds";
	private static final String TRANSITION_DURATION = "banking.accounts.transition.duration.seconds";
	private static final String LOOKUP_DURATION = "banking.accounts.lookup.duration.seconds";
	private static final String ERRORS = "banking.accounts.errors.total";
	private static final String ACTIVE_ACCOUNTS_GAUGE = "banking.accounts.active.gauge";

	private static final String TAG_ACCOUNT_TYPE = "account_type";
	private static final String TAG_ACTION = "action";
	private static final String TAG_RESULT = "result";
	private static final String TAG_ERROR_CODE = "error_code";

	private static final String RESULT_SUCCESS = "success";
	private static final String RESULT_FAILURE = "failure";

	private final MeterRegistry registry;

	public AccountMetrics (MeterRegistry registry) {
		this.registry = registry;
	}

	public <T> T timeAccountOpening (AccountType type, Supplier<T> operation) {
		Timer timer = Timer.builder(OPEN_DURATION)
			.description("Time taken to open a new bank account")
			.tag(TAG_ACCOUNT_TYPE, type.name().toLowerCase())
			.publishPercentileHistogram()
			.sla(Duration.ofMillis(200), Duration.ofMillis(500), Duration.ofSeconds(1))
			.register(registry);

		try {
			T result = timer.record(operation);

			recordAccountOpened(type);

			return result;

		} catch (Exception e) {
			recordError(extractErrorCode(e));

			throw e;
		}
	}

	public <T> T timeStatusTransition (String action, Supplier<T> operation) {
		Timer timer = Timer.builder(TRANSITION_DURATION)
			.description("Time taken to apply a status transition")
			.tag(TAG_ACTION, action.toLowerCase())
			.register(registry);

		try {
			T result = timer.record(operation);

			recordStatusTransition(action, RESULT_SUCCESS);

			return result;

		} catch (Exception e) {
			recordStatusTransition(action, RESULT_FAILURE);

			recordError(extractErrorCode(e));

			throw e;
		}
	}

	public <T> T timeLookup (Supplier<T> operation) {
		return Timer.builder(LOOKUP_DURATION)
			.description("Time taken to look up an account by ID")
			.publishPercentileHistogram()
			.register(registry)
			.record(operation);
	}

	public void registerActiveAccountsGauge (Supplier<Number> supplier) {
		Gauge.builder(ACTIVE_ACCOUNTS_GAUGE, supplier)
			.description("Current number of accounts in ACTIVE status")
			.register(registry);
	}

	private void recordAccountOpened (AccountType type) {
		Counter.builder(ACCOUNTS_OPENED)
			.description("Total number of bank accounts opened")
			.tag(TAG_ACCOUNT_TYPE, type.name().toLowerCase())
			.tag(TAG_RESULT, RESULT_SUCCESS)
			.register(registry)
			.increment();
	}

	private void recordStatusTransition (String action, String result) {
		Counter.builder(STATUS_TRANSITIONS)
			.description("Total number of account status transitions")
			.tag(TAG_ACTION, action.toLowerCase())
			.tag(TAG_RESULT, result)
			.register(registry)
			.increment();
	}

	void recordError (String errorCode) {
		Counter.builder(ERRORS)
			.description("Total number of account operation errors")
			.tag(TAG_ERROR_CODE, errorCode)
			.register(registry)
			.increment();
	}

	private String extractErrorCode (Exception e) {
		return e instanceof BusinessException be ? be.getErrorCode() : "unknown";
	}
}
