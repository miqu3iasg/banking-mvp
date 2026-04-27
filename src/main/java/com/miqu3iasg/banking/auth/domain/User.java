package com.miqu3iasg.banking.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
	@Index(name = "idx_users_email", columnList = "email", unique = true),
	@Index(name = "idx_users_status", columnList = "status"),
	@Index(name = "idx_users_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@Setter
@Builder
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 320)
	private String email;

	@Column(name = "email_hash", nullable = false, unique = true, length = 64)
	private String emailHash;

	@Column(name = "password_hash", length = 120)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	@Builder.Default
	private AccountStatus status = AccountStatus.PENDING_VERIFICATION;

	@Column(name = "email_verified", nullable = false)
	@Builder.Default
	private boolean emailVerified = false;

	@Column(name = "mfa_enabled", nullable = false)
	@Builder.Default
	private boolean mfaEnabled = false;

	@Column(name = "mfa_secret", length = 512)
	private String mfaSecret;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(
		name = "user_roles",
		joinColumns = @JoinColumn(name = "user_id"),
		inverseJoinColumns = @JoinColumn(name = "role_id")
	)
	@Builder.Default
	private Set<RoleEntity> roles = new HashSet<>();

	@Column(name = "failed_login_attempts")
	@Builder.Default
	private int failedLoginAttempts = 0;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "password_changed_at")
	private Instant passwordChangedAt;

	@Column(name = "password_expires_at")
	private Instant passwordExpiresAt;

	@Column(name = "force_password_reset", nullable = false)
	@Builder.Default
	private boolean forcePasswordReset = false;

	@Column(name = "suspension_reason", length = 500)
	private String suspensionReason;

	@Column(name = "suspension_timestamp")
	private Instant suspensionTimestamp;

	@Column(name = "consent_email", nullable = false)
	@Builder.Default
	private boolean consentEmail = false;

	@Column(name = "consent_timestamp")
	private Instant consentTimestamp;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private Instant updatedAt;

	@Version
	private Long version;

	public boolean isLocked () {
		return lockedUntil != null && Instant.now().isBefore(lockedUntil);
	}

	public boolean isPasswordExpired () {
		return passwordExpiresAt != null && Instant.now().isAfter(passwordExpiresAt);
	}

	public void incrementFailedAttempts () {
		this.failedLoginAttempts++;
	}

	public void resetFailedAttempts () {
		this.failedLoginAttempts = 0;
		this.lockedUntil = null;
	}

	public void lock (Instant until) {
		this.lockedUntil = until;
	}

	public void unlock () {
		this.lockedUntil = null;
		this.failedLoginAttempts = 0;
	}
}
