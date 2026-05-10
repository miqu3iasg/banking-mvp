package com.miqu3iasg.banking.auth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHistoryTest {

    @Test
    void builder_createsValidPasswordHistory() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        PasswordHistory history = PasswordHistory.builder()
                .id(id)
                .userId(userId)
                .passwordHash("bcrypt_hash_value")
                .createdAt(now)
                .build();

        assertEquals(id, history.getId());
        assertEquals(userId, history.getUserId());
        assertEquals("bcrypt_hash_value", history.getPasswordHash());
        assertEquals(now, history.getCreatedAt());
    }

    @Test
    void createdAt_returnsCorrectTimestamp() {
        Instant createdAt = Instant.now().minusSeconds(3600);
        PasswordHistory history = PasswordHistory.builder()
                .createdAt(createdAt)
                .build();

        assertEquals(createdAt, history.getCreatedAt());
    }

    @Test
    void passwordHash_returnsCorrectHash() {
        String hash = "bcrypt$12$abcdefghijklmnopqrstuv";
        PasswordHistory history = PasswordHistory.builder()
                .passwordHash(hash)
                .build();

        assertEquals(hash, history.getPasswordHash());
    }
}
