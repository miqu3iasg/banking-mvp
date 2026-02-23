package com.miqu3iasg.banking.shared.idempotency;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class IdempotencyMetrics {

	private static final String IDEMPOTENCY_HIT = "banking.idempotency.hit.total";
	private static final String IDEMPOTENCY_STORED = "banking.idempotency.stored.total";
	private static final String IDEMPOTENCY_MISS = "banking.idempotency.miss.total";
	private static final String LOOKUP_DURATION = "banking.idempotency.lookup.duration.seconds";
	private static final String STORE_DURATION = "banking.idempotency.store.duration.seconds";
	private static final String ERRORS = "banking.idempotency.errors.total";
	private static final String IDEMPOTENCY_DUPLICATE_COMMIT = "banking.idempotency.duplicate_commit.total";

	private static final String PURGE_DURATION = "banking.idempotency.purge.duration.seconds";
	private static final String PURGE_DELETED = "banking.idempotency.purge.deleted.total";
	private static final String PURGE_BATCH_DELETED = "banking.idempotency.purge.batch.deleted.total";
	private static final String PURGE_FAILURES = "banking.idempotency.purge.failures.total";
	private static final String PURGE_CAPPED = "banking.idempotency.purge.capped.total";

	private static final String TAG_OPERATION = "operation";
	private static final String TAG_ERROR_TYPE = "error_type";
	private static final String TAG_RESULT = "result";
	private static final String RESULT_SUCCESS = "success";
	private static final String RESULT_FAILURE = "failure";

	private final MeterRegistry registry;

	public IdempotencyMetrics (MeterRegistry registry) {
		this.registry = registry;
	}

	/**
	 * Wraps {@code findCachedResponse} logic, records lookup duration,
	 * and increments hit / miss counters based on whether a cached value was found.
	 */
	public <T> T timeLookup (String operationType, boolean hit, Supplier<T> operation) {
		Timer timer = Timer.builder(LOOKUP_DURATION)
			.description("Time taken to look up an idempotency key")
			.tag(TAG_OPERATION, operationType.toLowerCase())
			.publishPercentileHistogram()
			.register(registry);

		try {
			T result = timer.record(operation);
			if (hit) {
				recordHit(operationType);
			} else {
				recordMiss(operationType);
			}
			return result;
		} catch (Exception e) {
			recordError(e.getClass().getSimpleName());
			throw e;
		}
	}

	/**
	 * Wraps {@code markProcessed} logic and records store duration.
	 */
	public void timeStore (String operationType, Runnable operation) {
		Timer timer = Timer.builder(STORE_DURATION)
			.description("Time taken to persist an idempotency key")
			.tag(TAG_OPERATION, operationType.toLowerCase())
			.register(registry);

		try {
			timer.record(operation);

			recordStored(operationType, RESULT_SUCCESS);

		} catch (Exception e) {
			recordStored(operationType, RESULT_FAILURE);

			recordError(e.getClass().getSimpleName());

			throw e;
		}
	}

	public int timePurge (Supplier<Integer> purgeLogic) {
		Timer timer = Timer.builder(PURGE_DURATION)
			.description("Total wall-clock time for a full purge job run")
			.tag(TAG_OPERATION, "purge")
			.publishPercentileHistogram()
			.register(registry);

		Integer result = timer.record(purgeLogic);

		return result != null ? result : 0;
	}

	public void recordPurgeSuccess (int totalDeleted) {
		Counter.builder(PURGE_DELETED)
			.description("Total number of expired idempotency keys deleted across all batches")
			.tag(TAG_OPERATION, "purge")
			.tag(TAG_RESULT, RESULT_SUCCESS)
			.register(registry)
			.increment(totalDeleted);
	}

	public void recordPurgeFailure (String errorType) {
		Counter.builder(PURGE_FAILURES)
			.description("Total number of purge job failures")
			.tag(TAG_OPERATION, "purge")
			.tag(TAG_ERROR_TYPE, errorType)
			.register(registry)
			.increment();
	}

	public void recordPurgeBatch (int batchDeleted) {
		Counter.builder(PURGE_BATCH_DELETED)
			.description("Number of expired idempotency keys deleted in a single batch")
			.tag(TAG_OPERATION, "purge")
			.register(registry)
			.increment(batchDeleted);
	}

	public void recordPurgeCapped () {
		Counter.builder(PURGE_CAPPED)
			.description("Purge job hit the maxBatches cap, indicating a large backlog")
			.tag(TAG_OPERATION, "purge")
			.register(registry)
			.increment();
	}

	void recordHit (String operationType) {
		Counter.builder(IDEMPOTENCY_HIT)
			.description("Total number of idempotency cache hits")
			.tag(TAG_OPERATION, operationType.toLowerCase())
			.register(registry)
			.increment();
	}

	void recordMiss (String operationType) {
		Counter.builder(IDEMPOTENCY_MISS)
			.description("Total number of idempotency cache misses")
			.tag(TAG_OPERATION, operationType.toLowerCase())
			.register(registry)
			.increment();
	}

	private void recordStored (String operationType, String result) {
		Counter.builder(IDEMPOTENCY_STORED)
			.description("Total number of idempotency keys persisted")
			.tag(TAG_OPERATION, operationType.toLowerCase())
			.tag(TAG_RESULT, result)
			.register(registry)
			.increment();
	}

	void recordError (String errorType) {
		Counter.builder(ERRORS)
			.description("Total number of idempotency operation errors")
			.tag(TAG_ERROR_TYPE, errorType)
			.register(registry)
			.increment();
	}

	void recordDuplicateCommit (String operationType) {
		Counter.builder(IDEMPOTENCY_DUPLICATE_COMMIT)
			.description("Concurrent writes for the same idempotency key, safely ignored")
			.tag(TAG_OPERATION, operationType.toLowerCase())
			.register(registry)
			.increment();
	}
}
