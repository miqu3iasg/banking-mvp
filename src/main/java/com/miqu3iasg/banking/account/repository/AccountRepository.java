package com.miqu3iasg.banking.account.repository;

import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.domain.AccountStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByAccountNumber (String accountNumber);

	boolean existsByDocumentNumber (String documentNumber);

	boolean existsByAccountNumber(String accountNumber);

	@Query(value = "SELECT LPAD(nextval('account_number_seq')::text, 8, '0')", nativeQuery = true)
	String generateAccountNumber ();

	@Lock(LockModeType.OPTIMISTIC)
	@QueryHints(
		@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")
	)
	@Query("SELECT a FROM Account a WHERE a.id = :id")
	Optional<Account> findByIdWithOptimisticLock (@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(
		@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")
	)
	@Query("SELECT a FROM Account a WHERE a.id = :id")
	Optional<Account> findByIdWithPessimisticLock (@Param("id") UUID id);

	@Query("SELECT COUNT(a) FROM Account a WHERE a.status = :status")
	long countByStatus (@Param("status") AccountStatus status);

	default long countActiveAccounts () {
		return countByStatus(AccountStatus.ACTIVE);
	}
}
