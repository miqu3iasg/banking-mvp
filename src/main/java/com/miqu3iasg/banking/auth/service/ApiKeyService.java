package com.miqu3iasg.banking.auth.service;

import com.miqu3iasg.banking.auth.domain.ApiKey;
import com.miqu3iasg.banking.auth.domain.Permission;
import com.miqu3iasg.banking.auth.domain.User;
import com.miqu3iasg.banking.auth.exception.ApiKeyException;
import com.miqu3iasg.banking.auth.exception.AuthFaultCode;
import com.miqu3iasg.banking.auth.repository.ApiKeyRepository;
import com.miqu3iasg.banking.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final int KEY_LENGTH = 32;
    private static final String KEY_PREFIX = "bk_";
    private static final int PREFIX_RANDOM_CHARS = 5;

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final HashingService hashingService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public ApiKeyCreationResult createApiKey(UUID ownerId, String name, String description, Set<Permission> scopes, Set<String> allowedIps, Instant expiresAt) {
        User owner = userRepository.findById(ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Owner not found"));

        String rawKey = generateRawKey();
        String keyHash = hashingService.tokenHash(rawKey);
        String keyPrefix = buildKeyPrefix(rawKey);

        ApiKey apiKey = ApiKey.builder()
            .keyHash(keyHash)
            .keyPrefix(keyPrefix)
            .ownerId(ownerId)
            .name(name)
            .description(description)
            .scopes(scopes != null ? scopes : EnumSet.noneOf(Permission.class))
            .allowedIps(allowedIps != null ? allowedIps : new HashSet<>())
            .expiresAt(expiresAt)
            .build();

        apiKey = apiKeyRepository.save(apiKey);

        log.info("API key created: {} for owner: {}", keyPrefix, ownerId);

        return new ApiKeyCreationResult(apiKey, rawKey);
    }

    @Transactional(readOnly = true)
    public Optional<ApiKey> validateApiKey(String rawKey, String clientIp) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }

        String keyHash = hashingService.tokenHash(rawKey);
        Instant now = Instant.now();

        ApiKey matchingKey = apiKeyRepository.findValidKey(keyHash, now)
            .orElse(null);

        if (matchingKey == null) {
            String keyPrefix = buildKeyPrefix(rawKey);
            List<ApiKey> candidateKeys = apiKeyRepository.findByKeyPrefix(keyPrefix);
            if (!candidateKeys.isEmpty()) {
                matchingKey = candidateKeys.stream()
                    .filter(key -> key.getKeyHash().equals(keyHash))
                    .findFirst()
                    .orElse(null);
            }
            if (matchingKey == null) {
                return Optional.empty();
            }
            if (matchingKey.isExpired()) {
                throw new ApiKeyException(AuthFaultCode.API_002);
            }
            if (matchingKey.isRevoked()) {
                throw new ApiKeyException(AuthFaultCode.API_003);
            }
            return Optional.empty();
        }

        if (matchingKey.getAllowedIps() != null && !matchingKey.getAllowedIps().isEmpty()) {
            if (!matchingKey.getAllowedIps().contains(clientIp)) {
                throw new ApiKeyException(AuthFaultCode.API_004);
            }
        }

        apiKeyRepository.updateLastUsed(matchingKey.getId(), now);

        return Optional.of(matchingKey);
    }

    @Transactional
    public ApiKeyCreationResult rotateApiKey(UUID keyId, int gracePeriodDays) {
        ApiKey oldKey = apiKeyRepository.findById(keyId)
            .orElseThrow(() -> new IllegalArgumentException("API key not found"));

        ApiKeyCreationResult newKeyResult = createApiKey(
            oldKey.getOwnerId(),
            oldKey.getName() + " (rotated)",
            oldKey.getDescription(),
            oldKey.getScopes(),
            oldKey.getAllowedIps(),
            oldKey.getExpiresAt()
        );

        ApiKey newKey = newKeyResult.apiKey();
        newKey.setRotatedFromKeyId(keyId);
        newKey.setRotationGracePeriodEnd(Instant.now().plusSeconds(gracePeriodDays * 24 * 60 * 60L));
        apiKeyRepository.save(newKey);

        log.info("API key rotated: {} -> {}", oldKey.getKeyPrefix(), newKey.getKeyPrefix());

        return newKeyResult;
    }

    @Transactional
    public void revokeApiKey(UUID keyId, String reason) {
        ApiKey key = apiKeyRepository.findById(keyId)
            .orElseThrow(() -> new IllegalArgumentException("API key not found"));

        key.setRevoked(true);
        key.setRevokedAt(Instant.now());
        key.setRevokedReason(reason);
        apiKeyRepository.save(key);

        log.info("API key revoked: {} reason: {}", key.getKeyPrefix(), reason);
    }

    @Transactional(readOnly = true)
    public List<ApiKey> listApiKeys(UUID ownerId) {
        return apiKeyRepository.findByOwnerId(ownerId);
    }

    public boolean hasScope(ApiKey apiKey, Permission requiredScope) {
        return apiKey != null && apiKey.getScopes().contains(requiredScope);
    }

    public boolean hasAnyScope(ApiKey apiKey, Set<Permission> requiredScopes) {
        if (apiKey == null || requiredScopes == null) {
            return false;
        }
        return apiKey.getScopes().stream().anyMatch(requiredScopes::contains);
    }

    private String generateRawKey() {
        byte[] bytes = new byte[KEY_LENGTH];
        secureRandom.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String buildKeyPrefix(String rawKey) {
        return KEY_PREFIX + rawKey.substring(KEY_PREFIX.length(), KEY_PREFIX.length() + PREFIX_RANDOM_CHARS);
    }

    public record ApiKeyCreationResult(ApiKey apiKey, String rawKey) {}
}
