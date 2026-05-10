package com.miqu3iasg.banking.auth.fixture;

import com.miqu3iasg.banking.auth.domain.*;

import java.time.Instant;
import java.util.UUID;

/** Simple test data builders for authentication domain objects. */
public class TestDataFactory {

    public static UserBuilder user() {
        return new UserBuilder();
    }

    public static RefreshTokenBuilder refreshToken() {
        return new RefreshTokenBuilder();
    }

    public static MfaBackupCodeBuilder mfaBackupCode() {
        return new MfaBackupCodeBuilder();
    }

    // ---- Builders -------------------------------------------------------
    public static class UserBuilder {
        private UUID id = UUID.randomUUID();
        private String email = "user@example.com";
        private String passwordHash = "hash"; // assume already hashed
        private AccountStatus status = AccountStatus.ACTIVE;

        public UserBuilder id(UUID id) { this.id = id; return this; }
        public UserBuilder email(String email) { this.email = email; return this; }
        public UserBuilder passwordHash(String hash) { this.passwordHash = hash; return this; }
        public UserBuilder status(AccountStatus status) { this.status = status; return this; }

        public User build() {
            User user = new User();
            user.setId(id);
            user.setEmail(email);
            user.setPasswordHash(passwordHash);
            user.setStatus(status);
            return user;
        }
    }

    public static class RefreshTokenBuilder {
        private UUID id = UUID.randomUUID();
        private UUID userId = UUID.randomUUID();
        private String tokenHash = java.util.UUID.randomUUID().toString();
        private Instant expiresAt = Instant.now().plusSeconds(3600);

        public RefreshTokenBuilder id(UUID id) { this.id = id; return this; }
        public RefreshTokenBuilder userId(UUID userId) { this.userId = userId; return this; }
        public RefreshTokenBuilder tokenHash(String tokenHash) { this.tokenHash = tokenHash; return this; }
        public RefreshTokenBuilder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }

        public RefreshToken build() {
            RefreshToken rt = new RefreshToken();
            rt.setId(id);
            rt.setUserId(userId);
            rt.setTokenHash(tokenHash);
            rt.setExpiresAt(expiresAt);
            return rt;
        }
    }

    public static class MfaBackupCodeBuilder {
        private UUID id = UUID.randomUUID();
        private UUID userId = UUID.randomUUID();
        private String codeHash = java.util.UUID.randomUUID().toString();
        private boolean used = false;

        public MfaBackupCodeBuilder id(UUID id) { this.id = id; return this; }
        public MfaBackupCodeBuilder userId(UUID userId) { this.userId = userId; return this; }
        public MfaBackupCodeBuilder codeHash(String codeHash) { this.codeHash = codeHash; return this; }
        public MfaBackupCodeBuilder used(boolean used) { this.used = used; return this; }

        public MfaBackupCode build() {
            MfaBackupCode backup = new MfaBackupCode();
            backup.setId(id);
            backup.setUserId(userId);
            backup.setCodeHash(codeHash);
            backup.setUsed(used);
            return backup;
        }
    }
}
