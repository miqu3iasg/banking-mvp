package com.miqu3iasg.banking.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "api_keys", indexes = {
    @Index(name = "idx_api_key_key_hash", columnList = "key_hash", unique = true),
    @Index(name = "idx_api_key_owner_id", columnList = "owner_id"),
    @Index(name = "idx_api_key_expires_at", columnList = "expires_at"),
    @Index(name = "idx_api_key_key_prefix", columnList = "key_prefix")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 8)
    private String keyPrefix;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String description;

    @ElementCollection(targetClass = Permission.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "api_key_scopes", joinColumns = @JoinColumn(name = "api_key_id"))
    @Column(name = "scope")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Permission> scopes = EnumSet.noneOf(Permission.class);

    @ElementCollection(targetClass = String.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "api_key_allowed_ips", joinColumns = @JoinColumn(name = "api_key_id"))
    @Column(name = "allowed_ip")
    @Builder.Default
    private Set<String> allowedIps = new HashSet<>();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 200)
    private String revokedReason;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "rotated_from_key_id")
    private UUID rotatedFromKeyId;

    @Column(name = "rotation_grace_period_end")
    private Instant rotationGracePeriodEnd;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isInRotationGracePeriod() {
        return rotationGracePeriodEnd != null && Instant.now().isBefore(rotationGracePeriodEnd);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
