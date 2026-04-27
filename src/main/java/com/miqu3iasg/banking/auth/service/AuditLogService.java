package com.miqu3iasg.banking.auth.service;

import com.miqu3iasg.banking.auth.domain.AuditLog;
import com.miqu3iasg.banking.auth.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_SPAN_ID = "spanId";

    private final AuditLogRepository auditLogRepository;
    private final HashingService hashingService;

    @Async("authTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLoginAttempt(UUID userId, String emailHash, String ipHash, String userAgentHash, String outcome, String failureReason) {
        try {
            AuditLog auditLog = AuditLog.builder()
                .eventType("LOGIN")
                .userId(userId)
                .emailHash(emailHash)
                .ipHash(ipHash)
                .userAgentHash(userAgentHash)
                .outcome(outcome)
                .failureReason(failureReason)
                .traceId(MDC.get(MDC_TRACE_ID))
                .spanId(MDC.get(MDC_SPAN_ID))
                .createdAt(Instant.now())
                .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to write audit log for login attempt", e);
            throw e;
        }
    }

    @Async("authTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logRegistration(UUID userId, String email, String ipHash, String outcome) {
        try {
            AuditLog auditLog = AuditLog.builder()
                .eventType("REGISTRATION")
                .userId(userId)
                .emailHash(email != null ? hashingService.emailHash(email) : null)
                .ipHash(ipHash)
                .outcome(outcome)
                .traceId(MDC.get(MDC_TRACE_ID))
                .spanId(MDC.get(MDC_SPAN_ID))
                .createdAt(Instant.now())
                .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to write audit log for registration", e);
            throw e;
        }
    }

    @Async("authTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTokenRefresh(UUID userId, String outcome, String failureReason) {
        try {
            AuditLog auditLog = AuditLog.builder()
                .eventType("TOKEN_REFRESH")
                .userId(userId)
                .outcome(outcome)
                .failureReason(failureReason)
                .traceId(MDC.get(MDC_TRACE_ID))
                .spanId(MDC.get(MDC_SPAN_ID))
                .createdAt(Instant.now())
                .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to write audit log for token refresh", e);
            throw e;
        }
    }

    @Async("authTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPasswordChange(UUID userId, String ipHash, String outcome) {
        try {
            AuditLog auditLog = AuditLog.builder()
                .eventType("PASSWORD_CHANGE")
                .userId(userId)
                .ipHash(ipHash)
                .outcome(outcome)
                .traceId(MDC.get(MDC_TRACE_ID))
                .spanId(MDC.get(MDC_SPAN_ID))
                .createdAt(Instant.now())
                .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to write audit log for password change", e);
            throw e;
        }
    }

    @Async("authTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logMfaAttempt(UUID userId, String ipHash, String outcome, String failureReason) {
        try {
            AuditLog auditLog = AuditLog.builder()
                .eventType("MFA_ATTEMPT")
                .userId(userId)
                .ipHash(ipHash)
                .outcome(outcome)
                .failureReason(failureReason)
                .traceId(MDC.get(MDC_TRACE_ID))
                .spanId(MDC.get(MDC_SPAN_ID))
                .createdAt(Instant.now())
                .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to write audit log for MFA attempt", e);
            throw e;
        }
    }

    @Async("authTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAdminAction(UUID adminId, String eventType, String resourceType, String resourceId, String outcome) {
        try {
            AuditLog auditLog = AuditLog.builder()
                .eventType(eventType)
                .userId(adminId)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .outcome(outcome)
                .traceId(MDC.get(MDC_TRACE_ID))
                .spanId(MDC.get(MDC_SPAN_ID))
                .createdAt(Instant.now())
                .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to write audit log for admin action", e);
            throw e;
        }
    }
}
