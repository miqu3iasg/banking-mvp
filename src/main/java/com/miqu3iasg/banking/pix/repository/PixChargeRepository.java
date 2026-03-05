package com.miqu3iasg.banking.pix.repository;

import com.miqu3iasg.banking.pix.domain.PixCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PixChargeRepository extends JpaRepository<PixCharge, UUID> {
	Optional<PixCharge> findByTxid (String txid);

	boolean existsByTxid (String txid);

	@Query("SELECT c FROM PixCharge c WHERE c.accountId = :accountId ORDER BY c.createdAt DESC")
	java.util.List<PixCharge> findByAccountIdOrderByCreatedAtDesc (@Param("accountId") UUID accountId);

	@Query("""
    SELECT c
    FROM PixCharge c
    WHERE c.status = com.miqu3iasg.banking.pix.domain.PixChargeStatus.PENDING
      AND c.expiresAt < :now
""")
	List<PixCharge> findExpiredPendingCharges(@Param("now") Instant now);
}
