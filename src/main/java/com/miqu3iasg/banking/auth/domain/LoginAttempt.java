package com.miqu3iasg.banking.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_attempts", indexes = {
    @Index(name = "idx_login_attempts_user_id", columnList = "user_id"),
    @Index(name = "idx_login_attempts_ip_hash", columnList = "ip_hash"),
    @Index(name = "idx_login_attempts_created_at", columnList = "created_at"),
    @Index(name = "idx_login_attempts_ip_created", columnList = "ip_hash, created_at"),
    @Index(name = "idx_login_attempts_user_created", columnList = "user_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "ip_hash", nullable = false, length = 64)
    private String ipHash;

    @Column(name = "user_agent_hash", length = 64)
    private String userAgentHash;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "failure_reason", length = 50)
    private String failureReason;

    @Column(name = "locked_out", nullable = false)
    @Builder.Default
    private boolean lockedOut = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
