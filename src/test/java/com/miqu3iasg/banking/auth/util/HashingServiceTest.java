package com.miqu3iasg.banking.auth.util;

import com.miqu3iasg.banking.auth.service.HashingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashingServiceTest {

    private final HashingService service = new HashingService();

    @Test
    void sha256_generatesConsistentHash() {
        String input = "test input";
        String hash1 = service.sha256(input);
        String hash2 = service.sha256(input);

        assertEquals(hash1, hash2);
    }

    @Test
    void sha256_differentInputs_differentHashes() {
        String hash1 = service.sha256("input1");
        String hash2 = service.sha256("input2");

        assertNotEquals(hash1, hash2);
    }

    @Test
    void sha256_nullInput_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> service.sha256(null));
    }

    @Test
    void emailHash_generatesConsistentHash() {
        String email = "test@example.com";
        String hash1 = service.emailHash(email);
        String hash2 = service.emailHash(email);

        assertEquals(hash1, hash2);
    }

    @Test
    void emailHash_differentEmails_differentHashes() {
        String hash1 = service.emailHash("user1@example.com");
        String hash2 = service.emailHash("user2@example.com");

        assertNotEquals(hash1, hash2);
    }

    @Test
    void ipHash_generatesConsistentHash() {
        String ip = "192.168.1.1";
        String hash1 = service.ipHash(ip);
        String hash2 = service.ipHash(ip);

        assertEquals(hash1, hash2);
    }

    @Test
    void userAgentHash_generatesConsistentHash() {
        String ua = "Mozilla/5.0";
        String hash1 = service.userAgentHash(ua);
        String hash2 = service.userAgentHash(ua);

        assertEquals(hash1, hash2);
    }

    @Test
    void tokenHash_generatesConsistentHash() {
        String token = "test-token-123";
        String hash1 = service.tokenHash(token);
        String hash2 = service.tokenHash(token);

        assertEquals(hash1, hash2);
    }

    @Test
    void generateSecureToken_generatesUniqueTokens() {
        String token1 = service.generateSecureToken();
        String token2 = service.generateSecureToken();

        assertNotEquals(token1, token2);
    }

    @Test
    void hashToken_isAliasForTokenHash() {
        String token = "test-token";
        String hash1 = service.hashToken(token);
        String hash2 = service.tokenHash(token);

        assertEquals(hash1, hash2);
    }
}
