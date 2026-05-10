package com.miqu3iasg.banking.auth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptTest {

    @Test
    void builder_createsValidLoginAttempt() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        LoginAttempt attempt = LoginAttempt.builder()
                .id(id)
                .userId(userId)
                .emailHash("emailhash")
                .ipHash("iphash")
                .userAgentHash("uahash")
                .success(true)
                .build();

        assertEquals(id, attempt.getId());
        assertEquals(userId, attempt.getUserId());
        assertEquals("emailhash", attempt.getEmailHash());
        assertEquals("iphash", attempt.getIpHash());
        assertTrue(attempt.isSuccess());
    }

    @Test
    void successFlag_returnsCorrectValue() {
        LoginAttempt successAttempt = LoginAttempt.builder().success(true).build();
        LoginAttempt failureAttempt = LoginAttempt.builder().success(false).build();

        assertTrue(successAttempt.isSuccess());
        assertFalse(failureAttempt.isSuccess());
    }

    @Test
    void failureReason_returnsCorrectValue() {
        LoginAttempt attempt = LoginAttempt.builder()
                .success(false)
                .failureReason("INVALID_PASSWORD")
                .build();

        assertEquals("INVALID_PASSWORD", attempt.getFailureReason());
    }

    @Test
    void lockedOutFlag_returnsCorrectValue() {
        LoginAttempt lockedOut = LoginAttempt.builder().lockedOut(true).build();
        LoginAttempt notLockedOut = LoginAttempt.builder().lockedOut(false).build();

        assertTrue(lockedOut.isLockedOut());
        assertFalse(notLockedOut.isLockedOut());
    }
}
