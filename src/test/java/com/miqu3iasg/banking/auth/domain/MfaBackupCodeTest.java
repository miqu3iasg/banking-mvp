package com.miqu3iasg.banking.auth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MfaBackupCodeTest {

    @Test
    void markUsed_setsUsedFlagAndTimestamp() {
        MfaBackupCode backupCode = new MfaBackupCode();
        Instant beforeMark = Instant.now();

        backupCode.markUsed();

        assertTrue(backupCode.isUsed());
        assertNotNull(backupCode.getUsedAt());
        assertTrue(backupCode.getUsedAt().isAfter(beforeMark.minusSeconds(1)));
        assertTrue(backupCode.getUsedAt().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    void builder_createsValidBackupCode() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        MfaBackupCode backupCode = MfaBackupCode.builder()
                .id(id)
                .userId(userId)
                .codeHash("codehash123")
                .build();

        assertEquals(id, backupCode.getId());
        assertEquals(userId, backupCode.getUserId());
        assertEquals("codehash123", backupCode.getCodeHash());
        assertFalse(backupCode.isUsed());
    }

    @Test
    void codeHash_returnsCorrectValue() {
        MfaBackupCode backupCode = MfaBackupCode.builder()
                .codeHash("test_hash_value")
                .build();

        assertEquals("test_hash_value", backupCode.getCodeHash());
    }
}
