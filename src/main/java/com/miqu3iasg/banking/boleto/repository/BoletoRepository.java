package com.miqu3iasg.banking.boleto.repository;

import com.miqu3iasg.banking.boleto.domain.Boleto;
import com.miqu3iasg.banking.boleto.domain.BoletoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BoletoRepository extends JpaRepository<Boleto, UUID> {

	Optional<Boleto> findByProviderChargeId (long providerChargeId);

	List<Boleto> findAllByStatusAndDueDateBefore (BoletoStatus status, LocalDate date);

	/**
	 * Efficient expiration query using partial index on (due_date) WHERE status = 'PENDING'.
	 * Equivalent to {@code findAllByStatusAndDueDateBefore} but relies on the filtered index
	 * defined in the Flyway migration for performance at scale.
	 */
	@Query("SELECT b FROM Boleto b WHERE b.status = 'PENDING' AND b.dueDate < :today")
	List<Boleto> findAllPendingOverdue (LocalDate today);
}
