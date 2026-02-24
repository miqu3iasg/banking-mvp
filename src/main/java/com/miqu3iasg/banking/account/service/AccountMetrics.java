package com.miqu3iasg.banking.account.service;

import com.miqu3iasg.banking.account.domain.AccountType;
import com.miqu3iasg.banking.shared.exception.BusinessException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class AccountMetrics {

	private static final String ACCOUNTS_OPENED = "banking.accounts.opened.total";
	private static final String STATUS_TRANSITIONS = "banking.accounts.status.transitions.total";
	private static final String LOCK_RETRIES = "banking.accounts.lock.retries.total";  // NEW (TODO 5)
	private static final String OPEN_DURATION = "banking.accounts.open.duration.seconds";
	private static final String TRANSITION_DURATION = "banking.accounts.transition.duration.seconds";
	private static final String TRANSITION_DB_DURATION = "banking.accounts.transition.db.duration.seconds";
	private static final String LOOKUP_DURATION = "banking.accounts.lookup.duration.seconds";
	private static final String ERRORS = "banking.accounts.errors.total";
	private static final String ACTIVE_ACCOUNTS_GAUGE = "banking.accounts.active.gauge";

	private static final String TAG_ACCOUNT_TYPE = "account_type";
	private static final String TAG_ACTION = "action";
	private static final String TAG_RESULT = "result";
	private static final String TAG_ERROR_CODE = "error_code";
	private static final String TAG_ERROR_CLASS = "error_class";
	private static final String TAG_RETRY_ATTEMPT = "retry_attempt";

	private static final String RESULT_SUCCESS = "success";
	private static final String RESULT_FAILURE = "failure";

	private final MeterRegistry registry;

	/**
	 * Cache of DB-write timers keyed by action name.
	 * Built once per action, reused on every subsequent call — no builder allocation in the hot path.
	 */
	private final ConcurrentHashMap<String, Timer> dbWriteTimers = new ConcurrentHashMap<>();

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
			recordError(extractErrorCode(e), extractErrorClass(e));

			throw e;
		}
	}

	public <T> T timeStatusTransition (String action, AccountType accountType, Supplier<T> operation) {
		Timer timer = Timer.builder(TRANSITION_DURATION)
			.description("Time taken to apply a status transition end-to-end")
			.tag(TAG_ACTION, action.toLowerCase())
			.tag(TAG_ACCOUNT_TYPE, accountType.name().toLowerCase())
			.publishPercentileHistogram()
			.sla(Duration.ofMillis(200), Duration.ofMillis(500), Duration.ofSeconds(1))
			.register(registry);

		try {
			T result = timer.record(operation);

			recordStatusTransition(action, accountType, RESULT_SUCCESS);

			return result;

		} catch (Exception e) {
			recordStatusTransition(action, accountType, RESULT_FAILURE);

			recordError(extractErrorCode(e), extractErrorClass(e));

			throw e;
		}
	}

	public <T> T timeStatusTransition (String action, Supplier<T> operation) {
		return timeStatusTransition(action, null, operation);
	}

	/**
	 * Times the DB-layer write of a status transition: lock acquisition, mutation, and flush.
	 *
	 * <p>This is intentionally distinct from {@link #timeStatusTransition}, which times the
	 * full end-to-end transition including idempotency checks and outbox persistence.
	 * The delta between the two surfaces DB write latency in isolation — critical for
	 * separating a slow database from a slow application layer at scale.
	 *
	 * <p>Timers are built once per action name and cached — no Micrometer builder is
	 * allocated inside the hot path.
	 *
	 * <p>SLOs are tighter than the end-to-end transition SLOs: the DB write alone should
	 * complete within 150ms under normal conditions. A breach of the 500ms bucket is a
	 * signal to investigate lock contention or DB degradation.
	 *
	 * @param action    the lifecycle action name, used as a low-cardinality tag
	 * @param operation the DB write operation to time
	 * @return the result of {@code operation}
	 */
	public <T> T timeTransitionDbWrite (String action, Supplier<T> operation) {
		String actionKey = action.toLowerCase();

		Timer timer = dbWriteTimers.computeIfAbsent(actionKey, k ->
			Timer.builder(TRANSITION_DB_DURATION)
				.description("DB-layer latency of account status transition (lock acquisition + write)")
				.tag(TAG_ACTION, k)
				.publishPercentileHistogram()
				.sla(Duration.ofMillis(50), Duration.ofMillis(150), Duration.ofMillis(500))
				.register(registry)
		);

		try {
			return timer.record(operation);
		} catch (Exception e) {
			recordError(extractErrorCode(e), extractErrorClass(e));

			throw e;
		}
	}

	public <T> T timeLookup (Supplier<T> operation) {
		return Timer.builder(LOOKUP_DURATION)
			.description("Time taken to look up an account by ID")
			.publishPercentileHistogram()
			.sla(Duration.ofMillis(50), Duration.ofMillis(150), Duration.ofMillis(500))
			.register(registry)
			.record(operation);
	}

	public void registerActiveAccountsGauge (Supplier<Number> supplier) {
		Gauge.builder(ACTIVE_ACCOUNTS_GAUGE, supplier)
			.description("Current number of accounts in ACTIVE status")
			.register(registry);
	}

	public void recordLockRetry (String action, int retryAttempt) {
		Counter.builder(LOCK_RETRIES)
			.description("Total number of optimistic-lock retries by action and attempt number")
			.tag(TAG_ACTION, action.toLowerCase())
			.tag(TAG_RETRY_ATTEMPT, String.valueOf(retryAttempt))
			.register(registry)
			.increment();
	}

	void recordError (String errorCode) {
		recordError(errorCode, "");
	}

	private void recordAccountOpened (AccountType type) {
		Counter.builder(ACCOUNTS_OPENED)
			.description("Total number of bank accounts opened")
			.tag(TAG_ACCOUNT_TYPE, type.name().toLowerCase())
			.tag(TAG_RESULT, RESULT_SUCCESS)
			.register(registry)
			.increment();
	}

	private void recordStatusTransition (String action, AccountType accountType, String result) {
		String accountTypeTag = accountType != null ? accountType.name().toLowerCase() : "unknown";

		Counter.builder(STATUS_TRANSITIONS)
			.description("Total number of account status transitions")
			.tag(TAG_ACTION, action.toLowerCase())
			.tag(TAG_ACCOUNT_TYPE, accountTypeTag)
			.tag(TAG_RESULT, result)
			.register(registry)
			.increment();
	}

	private void recordError (String errorCode, String errorClass) {
		Counter.builder(ERRORS)
			.description("Total number of account operation errors")
			.tag(TAG_ERROR_CODE, errorCode)
			.tag(TAG_ERROR_CLASS, errorClass)
			.register(registry)
			.increment();
	}

	private String extractErrorCode (Exception e) {
		return e instanceof BusinessException be ? be.getErrorCode() : "unknown";
	}

	private String extractErrorClass (Exception e) {
		return e instanceof BusinessException ? "" : e.getClass().getSimpleName();
	}
}
