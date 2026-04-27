package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.LoginAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.ipHash = :ipHash AND la.createdAt > :since")
    long countByIpSince(@Param("ipHash") String ipHash, @Param("since") Instant since);

    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.userId = :userId AND la.createdAt > :since")
    long countByUserIdSince(@Param("userId") UUID userId, @Param("since") Instant since);

    Page<LoginAttempt> findByIpHash(String ipHash, Pageable pageable);

    Page<LoginAttempt> findByUserId(UUID userId, Pageable pageable);

    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.userId = :userId AND la.success = true AND la.createdAt > :since")
    long countSuccessfulLoginsByUserSince(@Param("userId") UUID userId, @Param("since") Instant since);
}
