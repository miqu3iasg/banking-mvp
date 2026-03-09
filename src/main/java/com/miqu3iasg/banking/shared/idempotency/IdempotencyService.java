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

	private static final int AWAIT_MAX_ATTEMPTS = 100;
	private static final long AWAIT_POLL_MS = 100L;

	private final IdempotencyKeyRepository repository;
	private final ObjectMapper objectMapper;
	private final IdempotencyMetrics metrics;
	private final Clock clock;

	@Transactional(readOnly = true)
	public <T> Optional<T> findCachedResponse (String key, Class<T> responseType) {
		Optional<IdempotencyKey> record = repository
			.findByKey(key)
			.filter(ik -> !ik.isExpiredAt(clock))
			.filter(ik -> ik.getStatus() == IdempotencyKeyStatus.COMPLETED);

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

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markProcessed (String key, String operationType, Object response) {
		String responseBody = serialize(response);

		metrics.timeStore(operationType, () -> persistKey(key, operationType, responseBody));
	}

	private void persistKey (String key, String operationType, String responseBody) {

		try {
			IdempotencyKey record = IdempotencyKey.create(key, operationType, responseBody, IdempotencyKeyStatus.COMPLETED, clock);

			repository.save(record);

			log.debug(
				"Idempotency key stored: key=[{}] operation=[{}] expires=[{}]",
				key, operationType, record.getExpiresAt()
			);

		} catch (DataIntegrityViolationException e) {
			metrics.recordDuplicateCommit(operationType);

			log.debug("Idempotency key [{}] already committed by concurrent request (safe)", key);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean claimKey (String key, String operationType) {
		Instant now = Instant.now(clock);
		Instant expires = now.plus(IdempotencyKey.RETENTION);

		int inserted = repository.insertIfAbsent(key, operationType, now, expires);

		boolean winner = inserted == 1;

		if (!winner) {
			log.debug("Idempotency key [{}] already claimed by concurrent request", key);
			metrics.recordDuplicateCommit(operationType);
		}

		return winner;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void completeKey (String key, String operationType, Object response) {
		String responseBody = serialize(response);

		metrics.timeStore(operationType, () -> {
			repository.findByKey(key).ifPresent(ik -> {

				ik.complete(responseBody);

				repository.save(ik);

				log.debug("Idempotency key completed: key=[{}] operation=[{}]", key, operationType);
			});
		});
	}

	public <T> Optional<T> awaitCompletedResponse (String key, Class<T> responseType) {
		for (int attempt = 0; attempt < AWAIT_MAX_ATTEMPTS; attempt++) {
			Optional<T> result = pollForCompletion(key, responseType);

			if (result.isPresent()) {
				log.debug("Loser thread resolved key=[{}] after {} poll(s)", key, attempt + 1);
				return result;
			}

			try {
				Thread.sleep(AWAIT_POLL_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}

		log.warn("Timed out waiting for idempotency key=[{}] to complete after {}ms",
			key,
			(long) AWAIT_MAX_ATTEMPTS * AWAIT_POLL_MS);

		return Optional.empty();
	}

	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	public <T> Optional<T> pollForCompletion (String key, Class<T> responseType) {
		return repository.findByKey(key)
			.filter(ik -> ik.getStatus() == IdempotencyKeyStatus.COMPLETED)
			.filter(ik -> !ik.isExpiredAt(clock))
			.map(ik -> deserialize(ik.getResponseBody(), responseType));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deletePendingKey (String key) {
		repository.findByKey(key)
			.filter(ik -> ik.getStatus() == IdempotencyKeyStatus.PENDING)
			.ifPresent(ik -> {
				repository.delete(ik);
				log.debug("Deleted PENDING idempotency key=[{}] after operation failure", key);
			});
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
