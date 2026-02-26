package com.miqu3iasg.banking.shared.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		SELECT e FROM OutboxEvent e
		WHERE e.status = 'PENDING'
		  AND (e.lastAttemptAt IS NULL OR e.lastAttemptAt < :retryBefore)
		ORDER BY e.createdAt ASC
		LIMIT :limit
		""")
	List<OutboxEvent> findPendingForProcessing (
		@Param("retryBefore") Instant retryBefore,
		@Param("limit") int limit
	);
}
