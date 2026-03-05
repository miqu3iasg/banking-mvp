package com.miqu3iasg.banking.shared.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(
	name = "outbox.processor.enabled",
	havingValue = "true",
	matchIfMissing = true
)
public class OutboxProcessor {
	private static final int BATCH_SIZE = 50;
	private static final Duration BASE_BACKOFF = Duration.ofSeconds(10);

	private final OutboxRepository outboxRepository;
	private final Map<String, OutboxEventDispatcher> dispatchers;

	public OutboxProcessor (OutboxRepository outboxRepository, List<OutboxEventDispatcher> dispatchers) {
		this.outboxRepository = outboxRepository;

		this.dispatchers = dispatchers
			.stream()
			.collect(Collectors.toMap(
				OutboxEventDispatcher::eventType,
				Function.identity()
			));

		log.info(
			"OutboxProcessor initialized with {} dispatcher(s): {}",
			dispatchers.size(),
			this.dispatchers.keySet()
		);
	}

	@Transactional
	@Scheduled(fixedDelayString = "${outbox.processor.interval-ms:5000}")
	public void poolAndProcess () {
		Instant retryBefore = Instant.now();

		List<OutboxEvent> batch = outboxRepository.findPendingForProcessing(retryBefore, BATCH_SIZE);

		if (batch.isEmpty()) {
			return;
		}

		log.debug("OutboxProcessor polling: found {} pending event(s)", batch.size());

		batch.forEach(this::processEvent);
	}

	private void processEvent (OutboxEvent event) {
		OutboxEventDispatcher dispatcher = dispatchers.get(event.getEventType());

		if (dispatcher == null) {
			log.error(
				"No dispatcher registered for eventType={} outboxEventId={}; marking FAILED",
				event.getEventType(),
				event.getId()
			);

			event.markAttemptFailed();

			commitDispatchOutcome(event);

			return;
		}

		try {
			dispatcher.dispatch(event);

			event.markProcessed();

			commitDispatchOutcome(event);

			log.debug(
				"Outbox event processed: id={} eventType={} aggregateId={}",
				event.getId(),
				event.getEventType(),
				event.getAggregateId()
			);

		} catch (Exception ex) {
			event.markAttemptFailed();

			commitDispatchOutcome(event);

			if (event.isExhausted()) {
				log.error(
					"Outbox event exhausted after {} attempts — marked FAILED: id={} eventType={} aggregateId={} cause={}",
					OutboxEvent.MAX_ATTEMPTS,
					event.getId(),
					event.getEventType(),
					event.getAggregateId(),
					ex.getMessage(),
					ex
				);
			} else {
				Instant nextRetry = nextRetryAt(event.getAttempts());

				log.warn(
					"Outbox event attempt {}/{} failed; next retry after {}: id={} eventType={} aggregateId={} cause={}",
					event.getAttempts(),
					OutboxEvent.MAX_ATTEMPTS,
					nextRetry,
					event.getId(),
					event.getEventType(),
					event.getAggregateId(),
					ex.getMessage()
				);
			}
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	private void commitDispatchOutcome (OutboxEvent event) {
		outboxRepository.save(event);
	}

	/**
	 * Calculates the earliest instant at which a failed event becomes eligible
	 * for the next retry using exponential back-off.
	 *
	 * @param attempts the current attempt count after the failure just recorded
	 */
	private Instant nextRetryAt (int attempts) {
		long backoffSeconds = BASE_BACKOFF.toSeconds() * (1L << (attempts - 1));

		return Instant.now().plusSeconds(backoffSeconds);
	}
}
