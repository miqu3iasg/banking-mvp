package com.miqu3iasg.banking.shared.idempotency;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Getter
@Entity
@Table(
	name = "idempotency_keys",
	indexes = {
		@Index(name = "idx_idempotency_expires_at", columnList = "expires_at")
	}
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {
	static final Duration RETENTION = Duration.ofHours(24);

	/**
	 * The client-supplied idempotency key. Natural primary key; never generated
	 * by the server. Max 100 chars to accommodate UUID v4 (36 chars) with headroom
	 * for prefixed schemes (e.g. {@code "pay_<uuid>"}).
	 */
	@Id
	@Column(name = "idempotency_key", nullable = false, unique = true, updatable = false, length = 100)
	private String key;

	@Column(name = "response_body", columnDefinition = "TEXT", nullable = true, updatable = true)
	private String responseBody;

	@Column(name = "status", nullable = false, length = 20, updatable = true)
	@Enumerated(EnumType.STRING)
	private IdempotencyKeyStatus status;

	@Column(name = "operation_type", nullable = false, updatable = false, length = 50)
	private String operationType;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	private IdempotencyKey (String key, String operationType, String responseBody, IdempotencyKeyStatus status, Clock clock) {
		this.createdAt = Instant.now(clock);
		this.expiresAt = this.createdAt.plus(RETENTION);
		this.key = key;
		this.status = status;
		this.operationType = operationType;
		this.responseBody = responseBody;
	}

	public static IdempotencyKey create (
		String key,
		String operationType,
		String responseBody,
		IdempotencyKeyStatus status,
		Clock clock
	) {
		Assert.hasText(key, "key must not be blank");
		Assert.hasText(operationType, "operationType must not be blank");
		Assert.hasText(responseBody, "responseBody must not be blank");
		Objects.requireNonNull(clock, "clock must not be null");

		Assert.isTrue(key.length() <= 100, () -> "key must not exceed 100 characters, got: " + key.length());
		Assert.isTrue(operationType.length() <= 50, () -> "operationType must not exceed 50 characters, got: " + operationType.length());

		return new IdempotencyKey(key, operationType, responseBody, status, clock);
	}

	public boolean isExpired () {
		return isExpiredAt(Clock.systemUTC());
	}

	public boolean isExpiredAt (Clock clock) {
		Objects.requireNonNull(clock, "clock must not be null");
		return Instant.now(clock).isAfter(expiresAt);
	}

	public void complete (String responseBody) {
		Assert.hasText(responseBody, "responseBody must be not blank");
		this.responseBody = responseBody;
		this.status = IdempotencyKeyStatus.COMPLETED;
	}

	@Override
	public boolean equals (Object o) {
		if (this == o) return true;
		if (!(o instanceof IdempotencyKey other)) return false;
		return key != null && key.equals(other.key);
	}

	@Override
	public int hashCode () {
		return getClass().hashCode();
	}

	@Override
	public String toString () {
		return "IdempotencyKey{key='%s', operationType='%s', createdAt=%s, expiresAt=%s}"
			.formatted(key, operationType, createdAt, expiresAt);
	}
}
