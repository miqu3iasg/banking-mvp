package com.miqu3iasg.banking.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
public class HashingService {

    private final SecureRandom secureRandom = new SecureRandom();

    public String sha256(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public String sha256Truncated(String input, int maxLength) {
        if (input == null) {
            throw new IllegalArgumentException("Input must not be null");
        }
        String hash = sha256(input);
        if (hash.length() <= maxLength) {
            return hash;
        }
        return hash.substring(0, maxLength);
    }

    public String ipHash(String ipAddress) {
        if (ipAddress == null) {
            throw new IllegalArgumentException("IP address must not be null");
        }
        return sha256Truncated("ip:" + ipAddress, 32);
    }

    public String emailHash(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email must not be null");
        }
        return sha256Truncated("email:" + email.toLowerCase().trim(), 32);
    }

    public String userAgentHash(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return sha256Truncated("unknown:ua", 32);
        }
        String normalized = userAgent.length() > 200 ? userAgent.substring(0, 200) : userAgent;
        return sha256Truncated("ua:" + normalized, 32);
    }

    public String userIdHash(String userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }
        return sha256Truncated("user:" + userId, 32);
    }

    public String tokenHash(String token) {
        if (token == null) {
            throw new IllegalArgumentException("Token must not be null");
        }
        return sha256(token);
    }

    public String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String token) {
        return tokenHash(token);
    }
}
