package com.miqu3iasg.banking_mvp.auth.apikey;

import com.miqu3iasg.banking.auth.domain.ApiKey;
import com.miqu3iasg.banking.auth.domain.Permission;
import com.miqu3iasg.banking.auth.repository.ApiKeyRepository;
import com.miqu3iasg.banking.auth.service.HashingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Component
public class ApiKeyTestHelper {

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private HashingService hashingService;

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int KEY_LENGTH = 32;
    private static final String KEY_PREFIX = "bk_";

    public String createApiKeyForUser(UUID userId, String name, Set<Permission> scopes) {
        return createApiKeyForUser(userId, name, scopes, null, null);
    }

    public String createApiKeyForUser(UUID userId, String name, Set<Permission> scopes, Instant expiresAt) {
        return createApiKeyForUser(userId, name, scopes, expiresAt, null);
    }

    public String createApiKeyForUser(UUID userId, String name, Set<Permission> scopes, Instant expiresAt, Set<String> allowedIps) {
        String rawKey = generateRawKey();
        String keyHash = hashingService.tokenHash(rawKey);
        String keyPrefix = KEY_PREFIX + rawKey.substring(KEY_PREFIX.length(), KEY_PREFIX.length() + 5);

        ApiKey apiKey = ApiKey.builder()
                .keyHash(keyHash)
                .keyPrefix(keyPrefix)
                .ownerId(userId)
                .name(name)
                .description("Test API key")
                .scopes(scopes != null ? scopes : EnumSet.noneOf(Permission.class))
                .allowedIps(allowedIps != null ? allowedIps : Set.of())
                .expiresAt(expiresAt)
                .build();

        apiKeyRepository.save(apiKey);
        return rawKey;
    }

    public void revokeKey(UUID ownerId, String name, String reason) {
        apiKeyRepository.findByOwnerId(ownerId).stream()
                .filter(k -> k.getName().equals(name))
                .findFirst()
                .ifPresent(key -> {
                    key.setRevoked(true);
                    key.setRevokedAt(Instant.now());
                    key.setRevokedReason(reason);
                    apiKeyRepository.save(key);
                });
    }

    public ApiKey findKeyByName(UUID ownerId, String name) {
        return apiKeyRepository.findByOwnerId(ownerId).stream()
                .filter(k -> k.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + name));
    }

    private String generateRawKey() {
        byte[] bytes = new byte[KEY_LENGTH];
        secureRandom.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
