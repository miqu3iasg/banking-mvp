package com.miqu3iasg.banking.auth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetTokenTest {

    @Test
    void isExpired_whenExpiresAtInFuture_returnsFalse() {
        PasswordResetToken token = new PasswordResetToken();
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        assertFalse(token.isExpired());
    }

    @Test
    void isExpired_whenExpiresAtInPast_returnsTrue() {
        PasswordResetToken token = new PasswordResetToken();
        token.setExpiresAt(Instant.now().minusSeconds(3600));

        assertTrue(token.isExpired());
    }

    @Test
    void isValid_whenNotConsumedAndNotExpired_returnsTrue() {
        PasswordResetToken token = new PasswordResetToken();
        token.setConsumed(false);
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        assertTrue(token.isValid());
    }

    @Test
    void isValid_whenConsumed_returnsFalse() {
        PasswordResetToken token = new PasswordResetToken();
        token.setConsumed(true);
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        assertFalse(token.isValid());
    }

    @Test
    void isValid_whenExpired_returnsFalse() {
        PasswordResetToken token = new PasswordResetToken();
        token.setConsumed(false);
        token.setExpiresAt(Instant.now().minusSeconds(3600));

        assertFalse(token.isValid());
    }

    @Test
    void consume_setsConsumedFlagAndTimestamp() {
        PasswordResetToken token = new PasswordResetToken();
        Instant beforeConsume = Instant.now();

        token.consume();

        assertTrue(token.isConsumed());
        assertNotNull(token.getConsumedAt());
        assertTrue(token.getConsumedAt().isAfter(beforeConsume.minusSeconds(1)));
        assertTrue(token.getConsumedAt().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    void incrementAttempt_increasesCounter() {
        PasswordResetToken token = new PasswordResetToken();
        token.setAttemptCount(0);

        token.incrementAttempt();

        assertEquals(1, token.getAttemptCount());
    }

    @Test
    void incrementAttempt_whenMultipleAttempts_incrementsCorrectly() {
        PasswordResetToken token = new PasswordResetToken();
        token.setAttemptCount(0);

        token.incrementAttempt();
        token.incrementAttempt();
        token.incrementAttempt();

        assertEquals(3, token.getAttemptCount());
    }

    @Test
    void builder_createsValidToken() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PasswordResetToken token = PasswordResetToken.builder()
                .id(id)
                .userId(userId)
                .tokenHash("hash123")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        assertEquals(id, token.getId());
        assertEquals(userId, token.getUserId());
        assertFalse(token.isConsumed());
    }
}
