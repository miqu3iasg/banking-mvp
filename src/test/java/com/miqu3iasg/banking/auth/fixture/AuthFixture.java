package com.miqu3iasg.banking.auth.fixture;

import com.miqu3iasg.banking.auth.domain.*;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Test data builder for auth domain objects.
 */
public class AuthFixture {

    private AuthFixture() {}

    public static User createUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .emailHash(hash("test@example.com"))
                .status(AccountStatus.ACTIVE)
                .emailVerified(true)
                .mfaEnabled(false)
                .roles(new HashSet<>())
                .failedLoginAttempts(0)
                .lastLoginAt(Instant.now().minus(Duration.ofHours(1)))
                .passwordChangedAt(Instant.now().minus(Duration.ofDays(30)))
                .passwordExpiresAt(Instant.now().plus(Duration.ofDays(90)))
                .forcePasswordReset(false)
                .consentEmail(true)
                .consentTimestamp(Instant.now().minus(Duration.ofDays(1)))
                .createdAt(Instant.now().minus(Duration.ofDays(30)))
                .updatedAt(Instant.now())
                .build();
    }

    public static RoleEntity createRole() {
        return RoleEntity.builder()
                .id(UUID.randomUUID())
                .name(Role.ROLE_USER)
                .description("Test role")
                .permissions(EnumSet.of(Permission.ACCOUNT_READ))
                .mfaRequired(false)
                .build();
    }

    public static ApiKey createApiKey() {
        return ApiKey.builder()
                .id(UUID.randomUUID())
                .keyHash(hash(UUID.randomUUID().toString()))
                .keyPrefix("test_")
                .ownerId(UUID.randomUUID())
                .name("Test Key")
                .description("Test API Key")
                .scopes(EnumSet.of(Permission.ACCOUNT_READ))
                .allowedIps(new HashSet<>())
                .expiresAt(Instant.now().plus(Duration.ofDays(30)))
                .revoked(false)
                .createdAt(Instant.now().minus(Duration.ofDays(7)))
                .lastUsedAt(Instant.now().minus(Duration.ofHours(1)))
                .build();
    }

    public static RefreshToken createRefreshToken() {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash(hash(UUID.randomUUID().toString()))
                .userId(UUID.randomUUID())
                .familyId(UUID.randomUUID())
                .deviceFingerprint("test-device")
                .ipHash(hash("192.168.1.1"))
                .userAgentHash(hash("Mozilla/5.0"))
                .revoked(false)
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .createdAt(Instant.now().minus(Duration.ofHours(1)))
                .lastUsedAt(Instant.now().minus(Duration.ofMinutes(30)))
                .createdFromIpHash(hash("192.168.1.1"))
                .build();
    }

    public static EmailVerificationToken createEmailVerificationToken() {
        return EmailVerificationToken.builder()
                .id(UUID.randomUUID())
                .tokenHash(hash(UUID.randomUUID().toString()))
                .userId(UUID.randomUUID())
                .emailHash(hash("test@example.com"))
                .expiresAt(Instant.now().plus(Duration.ofHours(24)))
                .consumed(false)
                .createdAt(Instant.now().minus(Duration.ofMinutes(5)))
                .resendCount(0)
                .build();
    }

    public static PasswordResetToken createPasswordResetToken() {
        return PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .tokenHash(hash(UUID.randomUUID().toString()))
                .userId(UUID.randomUUID())
                .expiresAt(Instant.now().plus(Duration.ofHours(1)))
                .consumed(false)
                .createdAt(Instant.now().minus(Duration.ofMinutes(10)))
                .attemptCount(0)
                .createdFromIpHash(hash("192.168.1.1"))
                .build();
    }

    public static LoginAttempt createLoginAttempt() {
        return LoginAttempt.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .emailHash(hash("test@example.com"))
                .ipHash(hash("192.168.1.1"))
                .userAgentHash(hash("Mozilla/5.0"))
                .success(true)
                .lockedOut(false)
                .createdAt(Instant.now().minus(Duration.ofMinutes(5)))
                .build();
    }

    public static AuditLog createAuditLog() {
        return AuditLog.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .eventType("LOGIN_SUCCESS")
                .outcome("SUCCESS")
                .ipHash(hash("192.168.1.1"))
                .userAgentHash(hash("Mozilla/5.0"))
                .traceId(UUID.randomUUID().toString())
                .mfaRequired(false)
                .mfaCompleted(false)
                .createdAt(Instant.now().minus(Duration.ofMinutes(1)))
                .build();
    }

    public static MfaBackupCode createMfaBackupCode() {
        return MfaBackupCode.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .codeHash(hash("123456"))
                .used(false)
                .createdAt(Instant.now().minus(Duration.ofDays(1)))
                .build();
    }

    public static PasswordHistory createPasswordHistory() {
        return PasswordHistory.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .passwordHash("bcrypt_hash")
                .createdAt(Instant.now().minus(Duration.ofDays(30)))
                .build();
    }

    private static String hash(String input) {
        try {
            return java.util.Base64.getEncoder()
                    .encodeToString(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}