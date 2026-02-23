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

	/**
	 * Removes expired records in bulk.
	 * Should be invoked by a scheduled purge job (e.g. nightly maintenance).
	 */
	@Modifying
	@Transactional
	@Query(value = """
		DELETE FROM idempotency_keys
		WHERE expires_at < :expiredBefore
		LIMIT :batchSize
		""", nativeQuery = true)
	int deleteExpiredBefore (
		@Param("expiredBefore") Instant expiredBefore,
		@Param("batchSize") int batchSize
	);
}
