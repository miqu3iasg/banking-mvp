package com.miqu3iasg.banking.transaction.service;

import com.miqu3iasg.banking.shared.exception.BusinessException;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class TransactionMetrics {

	private static final String TRANSACTIONS_COMPLETED = "banking.transactions.completed.total";
	private static final String TRANSACTIONS_FAILED = "banking.transactions.failed.total";
	private static final String IDEMPOTENCY_HITS = "banking.transactions.idempotency.hits.total";
	private static final String TRANSACTION_DURATION = "banking.transactions.duration.seconds";
	private static final String TRANSACTION_AMOUNT = "banking.transactions.amount";
	private static final String PENDING_TRANSACTIONS = "banking.transactions.pending.gauge";
	private static final String ERRORS = "banking.transactions.errors.total";
	private static final String LOCK_RETRIES = "banking.transactions.lock.retries.total";

	private static final String TAG_TYPE = "type";
	private static final String TAG_RESULT = "result";
	private static final String TAG_ERROR_CODE = "error_code";
	private static final String TAG_CURRENCY = "currency";
	private static final String TAG_ACTION = "action";
	private static final String TAG_RETRY_ATTEMPT = "retry_attempt";

	private static final String RESULT_SUCCESS = "success";
	private static final String RESULT_FAILURE = "failure";

	private final MeterRegistry registry;

	public TransactionMetrics (MeterRegistry registry) {
		this.registry = registry;
	}

	public <T> T timeDeposit (String currency, Supplier<T> operation) {
		return timeTransaction(OperationType.DEPOSIT, currency, operation);
	}

	public <T> T timeWithdrawal (String currency, Supplier<T> operation) {
		return timeTransaction(OperationType.WITHDRAWAL, currency, operation);
	}

	public <T> T timeTransfer (String currency, Supplier<T> operation) {
		return timeTransaction(OperationType.TRANSFER, currency, operation);
	}

	public <T> T timeTransaction (OperationType type, String currency, Supplier<T> operation) {
		Timer timer = Timer.builder(TRANSACTION_DURATION)
			.description("Time taken to process a transaction end-to-end")
			.tag(TAG_TYPE, type.name().toLowerCase())
			.tag(TAG_CURRENCY, currency.toUpperCase())
			.publishPercentileHistogram()
			.sla(
				Duration.ofMillis(100),
				Duration.ofMillis(300),
				Duration.ofMillis(500),
				Duration.ofSeconds(1)
			)
			.register(registry);

		try {
			T result = timer.record(operation);
			recordCompleted(type, currency);
			return result;
		} catch (Exception e) {
			recordFailed(type, currency);
			recordError(extractErrorCode(e));
			throw e;
		}
	}

	public void recordTransactionAmount (TransactionType type, String currency, double amount) {
		DistributionSummary.builder(TRANSACTION_AMOUNT)
			.description("Distribution of transaction amounts")
			.tag(TAG_TYPE, type.name().toLowerCase())
			.tag(TAG_CURRENCY, currency.toUpperCase())
			.publishPercentileHistogram()
			.register(registry)
			.record(amount);
	}

	public void recordIdempotencyHit (TransactionType type) {
		Counter.builder(IDEMPOTENCY_HITS)
			.description("Total number of requests that returned a cached idempotent response")
			.tag(TAG_TYPE, type.name().toLowerCase())
			.register(registry)
			.increment();
	}

	public void registerPendingTransactionsGauge (Supplier<Number> supplier) {
		Gauge.builder(PENDING_TRANSACTIONS, supplier)
			.description("Current number of transactions in PENDING status")
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

	private void recordCompleted (OperationType type, String currency) {
		Counter.builder(TRANSACTIONS_COMPLETED)
			.description("Total number of successfully completed transactions")
			.tag(TAG_TYPE, type.name().toLowerCase())
			.tag(TAG_CURRENCY, currency.toUpperCase())
			.tag(TAG_RESULT, RESULT_SUCCESS)
			.register(registry)
			.increment();
	}

	private void recordFailed (OperationType type, String currency) {
		Counter.builder(TRANSACTIONS_FAILED)
			.description("Total number of failed transactions")
			.tag(TAG_TYPE, type.name().toLowerCase())
			.tag(TAG_CURRENCY, currency.toUpperCase())
			.tag(TAG_RESULT, RESULT_FAILURE)
			.register(registry)
			.increment();
	}

	void recordError (String errorCode) {
		Counter.builder(ERRORS)
			.description("Total number of transaction operation errors")
			.tag(TAG_ERROR_CODE, errorCode)
			.register(registry)
			.increment();
	}

	private String extractErrorCode (Exception e) {
		return e instanceof BusinessException be ? be.getErrorCode() : "unknown";
	}
}
