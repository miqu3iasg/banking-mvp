package com.miqu3iasg.banking.transaction.repository;

import com.miqu3iasg.banking.transaction.domain.Transaction;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
	Optional<Transaction> findByIdempotencyKey (String idempotencyKey);

	@Query("""
		SELECT t FROM Transaction t
		WHERE t.accountId = :accountId
		  AND (:from IS NULL OR t.createdAt >= :from)
		  AND (:to   IS NULL OR t.createdAt <= :to)
		  AND (:type IS NULL OR t.type = :type)
		ORDER BY t.createdAt DESC
		""")
	Page<Transaction> findStatement (
		@Param("accountId") UUID accountId,
		@Param("from") Instant from,
		@Param("to") Instant to,
		@Param("type") TransactionType type,
		Pageable pageable
	);
}
