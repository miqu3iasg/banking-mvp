package com.miqu3iasg.banking.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_verification_tokens", indexes = {
    @Index(name = "idx_email_verif_token_hash", columnList = "token_hash", unique = true),
    @Index(name = "idx_email_verif_user_id", columnList = "user_id"),
    @Index(name = "idx_email_verif_expires_at", columnList = "expires_at"),
    @Index(name = "idx_email_verif_email_hash", columnList = "email_hash"),
    @Index(name = "idx_email_verif_last_resent", columnList = "last_resent_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "email_hash", nullable = false, length = 64)
    private String emailHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed", nullable = false)
    @Builder.Default
    private boolean consumed = false;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resend_count", nullable = false)
    @Builder.Default
    private int resendCount = 0;

    @Column(name = "last_resent_at")
    private Instant lastResentAt;

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

    public void incrementResendCount() {
        this.resendCount++;
        this.lastResentAt = Instant.now();
    }
}
