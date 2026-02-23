package com.miqu3iasg.banking.shared.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miqu3iasg.banking.shared.exception.IdempotencyCacheCorruptException;
import com.miqu3iasg.banking.shared.exception.PurgeInterruptedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

	private static final int PURGE_BATCH_SIZE = 1_000;
	private static final int PURGE_MAX_BATCHES = 500;
	private static final long PURGE_BATCH_DELAY_MS = 100L;

	private final IdempotencyKeyRepository repository;
	private final ObjectMapper objectMapper;
	private final IdempotencyMetrics metrics;
	private final Clock clock;

	/**
	 * Returns the cached response for the given idempotency key if one exists
	 * and has not expired, deserialized to the requested type.
	 *
	 * <p>A cache miss (key not found, or key expired) returns an empty
	 * {@link Optional}. The caller is responsible for executing the operation
	 * and subsequently calling {@link #markProcessed}.
	 *
	 * @param key          the idempotency key from the request header
	 * @param responseType the target type to deserialize the cached payload into
	 * @param <T>          response type
	 * @return a populated {@link Optional} on a valid cache hit; empty otherwise
	 */
	@Transactional(readOnly = true)
	public <T> Optional<T> findCachedResponse (String key, Class<T> responseType) {
		Optional<IdempotencyKey> record = repository
			.findByKey(key)
			.filter(ik -> !ik.isExpiredAt(clock));

		boolean hit = record.isPresent();

		String operationType = record
			.map(IdempotencyKey::getOperationType)
			.orElse("unknown");

		return metrics.timeLookup(operationType, hit, () ->
			record.map(ik -> {
				log.debug("Idempotency cache HIT: key=[{}] operation=[{}]", key, ik.getOperationType());

				return deserialize(ik.getResponseBody(), responseType);
			})
		);
	}

	/**
	 * Persists an idempotency record for the given key and response.
	 *
	 * <p>Runs in {@link Propagation#REQUIRES_NEW}: the record is committed in
	 * its own transaction, independently of the caller's transaction boundary.
	 * See class-level javadoc for the rationale.
	 *
	 * @param key           client-supplied idempotency key
	 * @param operationType human-readable operation classifier (e.g. "TRANSFER")
	 * @param response      the response object to serialise and store
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markProcessed (String key, String operationType, Object response) {
		String responseBody = serialize(response);

		metrics.timeStore(operationType, () -> persistKey(key, operationType, responseBody));
	}

	private void persistKey (String key, String operationType, String responseBody) {
		try {
			IdempotencyKey record = IdempotencyKey.create(key, operationType, responseBody, clock);

			repository.save(record);

			log.debug(
				"Idempotency key stored: key=[{}] operation=[{}] expires=[{}]",
				key, operationType, record.getExpiresAt()
			);

		} catch (DataIntegrityViolationException e) {
			// A concurrent request committed the same key first. This is expected
			// under normal race conditions; both requests executed the operation,
			// but only one record survives. The caller's response is still valid.
			metrics.recordDuplicateCommit(operationType);

			log.debug("Idempotency key [{}] already committed by concurrent request (safe)", key);
		}
	}

	@Scheduled(cron = "0 0 0 * * *")
	public void purgeExpiredKeys () {
		log.info("Idempotency purge job starting");

		Instant start = Instant.now(clock);

		int totalDeleted = 0;

		try {
			totalDeleted = metrics.timePurge(this::purgeInBatches);

			long elapsedMs = Duration.between(start, Instant.now(clock)).toMillis();

			log.info("Idempotency purge complete: deleted={} durationMs={}", totalDeleted, elapsedMs);

			metrics.recordPurgeSuccess(totalDeleted);

		} catch (Exception e) {
			log.error("Idempotency purge failed after deleting {} records", totalDeleted, e);

			metrics.recordPurgeFailure(e.getClass().getSimpleName());
			// Intentionally not rethrown; the scheduler thread must survive failures.
		}
	}

	private int purgeInBatches () {
		int totalDeleted = 0;
		int batchCount = 0;
		int deleted;

		do {
			deleted = repository.deleteExpiredBefore(Instant.now(clock), PURGE_BATCH_SIZE);
			totalDeleted += deleted;
			batchCount++;

			log.debug("Purge batch {}: deleted={} runningTotal={}", batchCount, deleted, totalDeleted);

			metrics.recordPurgeBatch(deleted);

			if (deleted == PURGE_BATCH_SIZE && batchCount < PURGE_MAX_BATCHES) {
				sleepBetweenBatches();
			}

		} while (deleted == PURGE_BATCH_SIZE && batchCount < PURGE_MAX_BATCHES);

		if (batchCount >= PURGE_MAX_BATCHES && deleted == PURGE_BATCH_SIZE) {
			log.warn(
				"Purge capped at {} batches — expired records may remain; consider tuning RETENTION or batch size",
				PURGE_MAX_BATCHES
			);

			metrics.recordPurgeCapped();
		}

		return totalDeleted;
	}

	private void sleepBetweenBatches () {
		try {
			Thread.sleep(PURGE_BATCH_DELAY_MS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PurgeInterruptedException(e);
		}
	}

	private String serialize (Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			log.warn(
				"Failed to serialise idempotency payload for type [{}]; caching skipped",
				obj.getClass().getName(), e
			);
			return "{}";
		}
	}

	private <T> T deserialize (String payload, Class<T> type) {
		try {
			return objectMapper.readValue(payload, type);
		} catch (JsonProcessingException e) {
			throw new IdempotencyCacheCorruptException(type, payload, e);
		}
	}
}
