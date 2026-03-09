package com.miqu3iasg.banking.transaction.repository;

import com.miqu3iasg.banking.transaction.domain.Transaction;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
	Optional<Transaction> findByIdempotencyKey (String idempotencyKey);

	List<Transaction> findByAccountId (UUID accountId);
	default Page<Transaction> findStatement(
		UUID accountId, Instant from, Instant to, TransactionType type, Pageable pageable) {

		return findAll(StatementSpec.build(accountId, from, to, type), pageable);
	}
}
