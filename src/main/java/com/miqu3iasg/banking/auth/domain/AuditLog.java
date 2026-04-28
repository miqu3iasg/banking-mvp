package com.miqu3iasg.banking.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_user_id", columnList = "user_id"),
    @Index(name = "idx_audit_event_type", columnList = "event_type"),
    @Index(name = "idx_audit_trace_id", columnList = "trace_id"),
    @Index(name = "idx_audit_auth_created_at", columnList = "created_at"),
    @Index(name = "idx_audit_outcome", columnList = "outcome"),
    @Index(name = "idx_audit_user_agent_hash", columnList = "user_agent_hash")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_id_hash", length = 64)
    private String userIdHash;

    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "user_agent_hash", length = 64)
    private String userAgentHash;

    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    @Column(name = "failure_reason", length = 50)
    private String failureReason;

    @Column(length = 1000)
    private String details;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "span_id", length = 32)
    private String spanId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(name = "mfa_required", nullable = false)
    @Builder.Default
    private boolean mfaRequired = false;

    @Column(name = "mfa_completed", nullable = false)
    @Builder.Default
    private boolean mfaCompleted = false;

    @Column(name = "impersonator_id")
    private UUID impersonatorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
