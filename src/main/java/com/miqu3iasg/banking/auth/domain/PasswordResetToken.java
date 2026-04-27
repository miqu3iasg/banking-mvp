package com.miqu3iasg.banking.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens", indexes = {
    @Index(name = "idx_pwd_reset_token_hash", columnList = "token_hash", unique = true),
    @Index(name = "idx_pwd_reset_user_id", columnList = "user_id"),
    @Index(name = "idx_pwd_reset_expires_at", columnList = "expires_at"),
    @Index(name = "idx_pwd_reset_created_from_ip_hash", columnList = "created_from_ip_hash")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed", nullable = false)
    @Builder.Default
    private boolean consumed = false;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_from_ip_hash", length = 64)
    private String createdFromIpHash;

    @Column(name = "attempt_count", columnDefinition = "integer DEFAULT 0 CHECK (attempt_count >= 0 AND attempt_count <= 10)")
    @Builder.Default
    private int attemptCount = 0;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !consumed && !isExpired();
    }

    public void consume() {
        this.consumed = true;
        this.consumedAt = Instant.now();
    }

    public void incrementAttempt() {
        this.attemptCount++;
    }
}
