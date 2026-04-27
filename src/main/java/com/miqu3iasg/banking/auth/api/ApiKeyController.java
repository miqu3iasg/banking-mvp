package com.miqu3iasg.banking.auth.api;

import com.miqu3iasg.banking.auth.api.dto.*;
import com.miqu3iasg.banking.auth.domain.ApiKey;
import com.miqu3iasg.banking.auth.domain.Permission;
import com.miqu3iasg.banking.auth.domain.User;
import com.miqu3iasg.banking.auth.service.ApiKeyService;
import com.miqu3iasg.banking.auth.service.ApiKeyService.ApiKeyCreationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CreateApiKeyResponse> createApiKey(@RequestBody CreateApiKeyRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        ApiKeyCreationResult result = apiKeyService.createApiKey(
                user.getId(),
                request.getName(),
                request.getDescription(),
                request.getScopes(),
                request.getAllowedIps(),
                request.getExpiresAt()
        );

        CreateApiKeyResponse response = CreateApiKeyResponse.builder()
                .id(result.apiKey().getId())
                .rawKey(result.rawKey())
                .keyPrefix(result.apiKey().getKeyPrefix())
                .name(result.apiKey().getName())
                .description(result.apiKey().getDescription())
                .scopes(result.apiKey().getScopes())
                .allowedIps(result.apiKey().getAllowedIps())
                .expiresAt(result.apiKey().getExpiresAt())
                .createdAt(result.apiKey().getCreatedAt())
                .build();

        log.info("API key created for user: {}", user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ApiKeySummaryResponse>> listApiKeys() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        List<ApiKey> keys = apiKeyService.listApiKeys(user.getId());
        List<ApiKeySummaryResponse> response = keys.stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiKeySummaryResponse> getApiKey(@PathVariable UUID id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        ApiKey key = apiKeyService.listApiKeys(user.getId()).stream()
                .filter(k -> k.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));

        return ResponseEntity.ok(toSummaryResponse(key));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> revokeApiKey(@PathVariable UUID id, @RequestParam String reason) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        ApiKey key = apiKeyService.listApiKeys(user.getId()).stream()
                .filter(k -> k.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));

        apiKeyService.revokeApiKey(id, reason);
        log.info("API key revoked: {} by user: {}", id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rotate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RotateApiKeyResponse> rotateApiKey(
            @PathVariable UUID id,
            @RequestParam int gracePeriodDays) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        ApiKey key = apiKeyService.listApiKeys(user.getId()).stream()
                .filter(k -> k.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));

        ApiKeyCreationResult result = apiKeyService.rotateApiKey(id, gracePeriodDays);

        RotateApiKeyResponse response = RotateApiKeyResponse.builder()
                .id(result.apiKey().getId())
                .rawKey(result.rawKey())
                .keyPrefix(result.apiKey().getKeyPrefix())
                .name(result.apiKey().getName())
                .description(result.apiKey().getDescription())
                .scopes(result.apiKey().getScopes())
                .allowedIps(result.apiKey().getAllowedIps())
                .expiresAt(result.apiKey().getExpiresAt())
                .createdAt(result.apiKey().getCreatedAt())
                .rotationGracePeriodEnd(result.apiKey().getRotationGracePeriodEnd())
                .build();

        log.info("API key rotated: {} -> {}", key.getKeyPrefix(), result.apiKey().getKeyPrefix());
        return ResponseEntity.ok(response);
    }

    private ApiKeySummaryResponse toSummaryResponse(ApiKey key) {
        return ApiKeySummaryResponse.builder()
                .id(key.getId())
                .keyPrefix(key.getKeyPrefix())
                .name(key.getName())
                .description(key.getDescription())
                .scopes(key.getScopes())
                .allowedIps(key.getAllowedIps())
                .expiresAt(key.getExpiresAt())
                .revoked(key.isRevoked())
                .lastUsedAt(key.getLastUsedAt())
                .createdAt(key.getCreatedAt())
                .build();
    }
}
