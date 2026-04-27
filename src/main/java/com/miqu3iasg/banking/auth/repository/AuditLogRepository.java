package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository("authAuditLogRepository")
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("SELECT al FROM AuditLog al WHERE al.userId = :userId")
    Page<AuditLog> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT al FROM AuditLog al WHERE al.eventType = :eventType")
    Page<AuditLog> findByEventType(@Param("eventType") String eventType, Pageable pageable);

    @Query("SELECT al FROM AuditLog al WHERE al.outcome = :outcome")
    Page<AuditLog> findByOutcome(@Param("outcome") String outcome, Pageable pageable);

    @Query("SELECT al FROM AuditLog al WHERE al.userId = :userId AND al.createdAt BETWEEN :start AND :end ORDER BY al.createdAt DESC")
    List<AuditLog> findByUserIdAndDateRange(@Param("userId") UUID userId, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT al FROM AuditLog al WHERE al.traceId = :traceId ORDER BY al.createdAt ASC")
    List<AuditLog> findByTraceId(@Param("traceId") String traceId);

    @Query("SELECT COUNT(al) FROM AuditLog al WHERE al.eventType = :eventType AND al.outcome = :outcome AND al.createdAt > :since")
    long countByEventTypeAndOutcomeSince(@Param("eventType") String eventType, @Param("outcome") String outcome, @Param("since") Instant since);

    @Query("SELECT al FROM AuditLog al WHERE al.ipHash = :ipHash AND al.createdAt > :since ORDER BY al.createdAt DESC")
    Page<AuditLog> findRecentByIpHash(@Param("ipHash") String ipHash, @Param("since") Instant since, Pageable pageable);

    @Query("SELECT al FROM AuditLog al WHERE al.eventType = 'TOKEN_REFRESH' AND al.outcome = 'FAILURE' AND al.failureReason = 'FAMILY_REUSE_DETECTED' AND al.createdAt > :since")
    List<AuditLog> findTokenReuseAttempts(@Param("since") Instant since);
}
