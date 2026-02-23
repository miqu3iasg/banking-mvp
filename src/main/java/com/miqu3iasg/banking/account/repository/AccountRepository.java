package com.miqu3iasg.banking.account.repository;

import com.miqu3iasg.banking.account.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
	Optional<Account> findByAccountNumber (String accountNumber);

	boolean existsByDocumentNumber (String documentNumber);

	@Query(value = "SELECT LPAD(nextval('account_number_seq')::text, 8, '0')", nativeQuery = true)
	String generateAccountNumber ();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT a FROM Account a WHERE a.id = :id")
	Optional<Account> findByIdWithLock(@Param("id") UUID id);
}
