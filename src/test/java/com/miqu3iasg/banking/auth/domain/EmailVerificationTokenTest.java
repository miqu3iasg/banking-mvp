package com.miqu3iasg.banking.auth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EmailVerificationTokenTest {

    @Test
    void isExpired_whenExpiresAtInFuture_returnsFalse() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        assertFalse(token.isExpired());
    }

    @Test
    void isExpired_whenExpiresAtInPast_returnsTrue() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setExpiresAt(Instant.now().minusSeconds(3600));

        assertTrue(token.isExpired());
    }

    @Test
    void isValid_whenNotConsumedAndNotExpired_returnsTrue() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setConsumed(false);
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        assertTrue(token.isValid());
    }

    @Test
    void isValid_whenConsumed_returnsFalse() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setConsumed(true);
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        assertFalse(token.isValid());
    }

    @Test
    void isValid_whenExpired_returnsFalse() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setConsumed(false);
        token.setExpiresAt(Instant.now().minusSeconds(3600));

        assertFalse(token.isValid());
    }

    @Test
    void consume_setsConsumedFlagAndTimestamp() {
        EmailVerificationToken token = new EmailVerificationToken();
        Instant beforeConsume = Instant.now();

        token.consume();

        assertTrue(token.isConsumed());
        assertNotNull(token.getConsumedAt());
        assertTrue(token.getConsumedAt().isAfter(beforeConsume.minusSeconds(1)));
        assertTrue(token.getConsumedAt().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    void incrementResendCount_incrementsCounter() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setResendCount(0);

        token.incrementResendCount();

        assertEquals(1, token.getResendCount());
        assertNotNull(token.getLastResentAt());
    }

    @Test
    void builder_createsValidToken() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(id)
                .userId(userId)
                .tokenHash("hash123")
                .emailHash("emailhash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        assertEquals(id, token.getId());
        assertEquals(userId, token.getUserId());
        assertFalse(token.isConsumed());
    }
}
