package com.miqu3iasg.banking.auth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyTest {

    @Test
    void isExpired_whenExpiresAtInFuture_returnsFalse() {
        ApiKey apiKey = new ApiKey();
        apiKey.setExpiresAt(Instant.now().plusSeconds(3600));

        assertFalse(apiKey.isExpired());
    }

    @Test
    void isExpired_whenExpiresAtInPast_returnsTrue() {
        ApiKey apiKey = new ApiKey();
        apiKey.setExpiresAt(Instant.now().minusSeconds(3600));

        assertTrue(apiKey.isExpired());
    }

    @Test
    void isInRotationGracePeriod_whenWithinGraceWindow_returnsTrue() {
        ApiKey apiKey = new ApiKey();
        apiKey.setRotationGracePeriodEnd(Instant.now().plusSeconds(3600));

        assertTrue(apiKey.isInRotationGracePeriod());
    }

    @Test
    void isInRotationGracePeriod_whenOutsideGraceWindow_returnsFalse() {
        ApiKey apiKey = new ApiKey();
        apiKey.setRotationGracePeriodEnd(Instant.now().minusSeconds(3600));

        assertFalse(apiKey.isInRotationGracePeriod());
    }

    @Test
    void isValid_whenNotRevokedAndNotExpired_returnsTrue() {
        ApiKey apiKey = new ApiKey();
        apiKey.setRevoked(false);
        apiKey.setExpiresAt(Instant.now().plusSeconds(3600));

        assertTrue(apiKey.isValid());
    }

    @Test
    void isValid_whenRevoked_returnsFalse() {
        ApiKey apiKey = new ApiKey();
        apiKey.setRevoked(true);
        apiKey.setExpiresAt(Instant.now().plusSeconds(3600));

        assertFalse(apiKey.isValid());
    }

    @Test
    void isValid_whenExpired_returnsFalse() {
        ApiKey apiKey = new ApiKey();
        apiKey.setRevoked(false);
        apiKey.setExpiresAt(Instant.now().minusSeconds(3600));

        assertFalse(apiKey.isValid());
    }

    @Test
    void builder_createsValidApiKey() {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        ApiKey apiKey = ApiKey.builder()
                .id(id)
                .ownerId(ownerId)
                .name("Test Key")
                .keyHash("hash123")
                .keyPrefix("bank_")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        assertEquals(id, apiKey.getId());
        assertEquals(ownerId, apiKey.getOwnerId());
        assertEquals("Test Key", apiKey.getName());
        assertFalse(apiKey.isRevoked());
    }
}
