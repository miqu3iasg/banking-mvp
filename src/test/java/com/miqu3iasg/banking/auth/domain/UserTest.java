package com.miqu3iasg.banking.auth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void isLocked_whenLockedUntilInFuture_returnsTrue() {
        User user = new User();
        user.setLockedUntil(Instant.now().plusSeconds(3600));

        assertTrue(user.isLocked());
    }

    @Test
    void isLocked_whenLockedUntilInPast_returnsFalse() {
        User user = new User();
        user.setLockedUntil(Instant.now().minusSeconds(3600));

        assertFalse(user.isLocked());
    }

    @Test
    void isLocked_whenLockedUntilIsNull_returnsFalse() {
        User user = new User();

        assertFalse(user.isLocked());
    }

    @Test
    void isPasswordExpired_whenExpiresAtInFuture_returnsFalse() {
        User user = new User();
        user.setPasswordExpiresAt(Instant.now().plusSeconds(3600));

        assertFalse(user.isPasswordExpired());
    }

    @Test
    void isPasswordExpired_whenExpiresAtInPast_returnsTrue() {
        User user = new User();
        user.setPasswordExpiresAt(Instant.now().minusSeconds(3600));

        assertTrue(user.isPasswordExpired());
    }

    @Test
    void isPasswordExpired_whenExpiresAtIsNull_returnsFalse() {
        User user = new User();

        assertFalse(user.isPasswordExpired());
    }

    @Test
    void incrementFailedAttempts_incrementsCounter() {
        User user = new User();
        user.setFailedLoginAttempts(0);

        user.incrementFailedAttempts();

        assertEquals(1, user.getFailedLoginAttempts());
    }

    @Test
    void resetFailedAttempts_resetsCounterToZero() {
        User user = new User();
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().plusSeconds(3600));

        user.resetFailedAttempts();

        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
    }

    @Test
    void lock_setsLockedUntilTimestamp() {
        User user = new User();
        Instant lockedUntil = Instant.now().plusSeconds(3600);

        user.lock(lockedUntil);

        assertEquals(lockedUntil, user.getLockedUntil());
    }

    @Test
    void unlock_clearsLockedUntilTimestamp() {
        User user = new User();
        user.setLockedUntil(Instant.now().plusSeconds(3600));
        user.setFailedLoginAttempts(5);

        user.unlock();

        assertNull(user.getLockedUntil());
        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    void builder_createsValidUser() {
        UUID id = UUID.randomUUID();
        String email = "test@example.com";
        String emailHash = "hash123";

        User user = User.builder()
                .id(id)
                .email(email)
                .emailHash(emailHash)
                .status(AccountStatus.ACTIVE)
                .emailVerified(true)
                .build();

        assertEquals(id, user.getId());
        assertEquals(email, user.getEmail());
        assertEquals(emailHash, user.getEmailHash());
        assertEquals(AccountStatus.ACTIVE, user.getStatus());
        assertTrue(user.isEmailVerified());
    }
}
