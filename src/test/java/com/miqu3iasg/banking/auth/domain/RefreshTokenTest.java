package com.miqu3iasg.banking.auth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenTest {

    @Test
    void isExpired_whenExpiresAtInFuture_returnsFalse() {
        RefreshToken token = new RefreshToken();
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        assertFalse(token.isExpired());
    }

    @Test
    void isExpired_whenExpiresAtInPast_returnsTrue() {
        RefreshToken token = new RefreshToken();
        token.setExpiresAt(Instant.now().minusSeconds(3600));

        assertTrue(token.isExpired());
    }

    @Test
    void isValid_whenNotRevokedAndNotExpired_returnsTrue() {
        RefreshToken token = new RefreshToken();
        token.setRevoked(false);
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        assertTrue(token.isValid());
    }

    @Test
    void isValid_whenRevoked_returnsFalse() {
        RefreshToken token = new RefreshToken();
        token.setRevoked(true);
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        assertFalse(token.isValid());
    }

    @Test
    void isValid_whenExpired_returnsFalse() {
        RefreshToken token = new RefreshToken();
        token.setRevoked(false);
        token.setExpiresAt(Instant.now().minusSeconds(3600));

        assertFalse(token.isValid());
    }

    @Test
    void builder_createsValidRefreshToken() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        RefreshToken token = RefreshToken.builder()
                .id(id)
                .userId(userId)
                .familyId(familyId)
                .tokenHash("hash123")
                .deviceFingerprint("test-device")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        assertEquals(id, token.getId());
        assertEquals(userId, token.getUserId());
        assertEquals(familyId, token.getFamilyId());
        assertFalse(token.isRevoked());
    }
}
