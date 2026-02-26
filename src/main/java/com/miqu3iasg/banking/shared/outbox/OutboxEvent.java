package com.miqu3iasg.banking.shared.outbox;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
	name = "outbox_events",
	indexes = {
		@Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
		@Index(name = "idx_outbox_aggregate", columnList = "aggregate_id")
	}
)
public class OutboxEvent {

	public static final int MAX_ATTEMPTS = 3;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(updatable = false, nullable = false)
	private UUID id;

	@Column(name = "event_type", nullable = false, length = 100, updatable = false)
	private String eventType;

	@Column(name = "aggregate_id", nullable = false, updatable = false)
	private String aggregateId;

	@Column(name = "payload", nullable = false, columnDefinition = "jsonb", updatable = false)
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private OutboxStatus status;

	@Column(name = "attempts", nullable = false)
	private int attempts;

	@Column(name = "last_attempt_at")
	private Instant lastAttemptAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected OutboxEvent () { }

	public static OutboxEvent of (String eventType, String aggregateId, String payload) {
		OutboxEvent event = new OutboxEvent();
		event.eventType = eventType;
		event.aggregateId = aggregateId;
		event.payload = payload;
		event.status = OutboxStatus.PENDING;
		event.attempts = 0;
		event.createdAt = Instant.now();
		return event;
	}

	public void markProcessed () {
		this.attempts++;
		this.lastAttemptAt = Instant.now();
		this.status = OutboxStatus.PROCESSED;
		this.processedAt = Instant.now();
	}

	public void markAttemptFailed () {
		this.attempts++;
		this.lastAttemptAt = Instant.now();

		if (this.attempts >= MAX_ATTEMPTS) {
			this.status = OutboxStatus.FAILED;
		}
	}

	public boolean isExhausted () {
		return this.attempts >= MAX_ATTEMPTS;
	}

	public boolean isPending () {
		return this.status == OutboxStatus.PENDING;
	}
}
