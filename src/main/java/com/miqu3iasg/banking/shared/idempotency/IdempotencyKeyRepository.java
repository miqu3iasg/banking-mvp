package com.miqu3iasg.banking.shared.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

	Optional<IdempotencyKey> findByKey (String key);

	@Modifying
	@Transactional
	@Query(value = """
		DELETE FROM idempotency_keys
		WHERE idempotency_key IN (
		    SELECT idempotency_key FROM idempotency_keys
		    WHERE expires_at < :expiredBefore
		    LIMIT :batchSize
		)
		""", nativeQuery = true)
	int deleteExpiredBefore (
		@Param("expiredBefore") Instant expiredBefore,
		@Param("batchSize") int batchSize
	);

	@Modifying
	@Transactional
	@Query(value = """
		INSERT INTO idempotency_keys
		    (idempotency_key, operation_type, response_body, status, created_at, expires_at)
		VALUES
		    (:key, :operationType, NULL, 'PENDING', :createdAt, :expiresAt)
		ON CONFLICT (idempotency_key) DO NOTHING
		""", nativeQuery = true)
	int insertIfAbsent (
		@Param("key") String key,
		@Param("operationType") String operationType,
		@Param("createdAt") Instant createdAt,
		@Param("expiresAt") Instant expiresAt
	);
}
