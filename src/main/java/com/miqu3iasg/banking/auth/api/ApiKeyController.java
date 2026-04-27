package com.miqu3iasg.banking.auth.api;

import com.miqu3iasg.banking.auth.api.dto.*;
import com.miqu3iasg.banking.auth.domain.ApiKey;
import com.miqu3iasg.banking.auth.domain.Permission;
import com.miqu3iasg.banking.auth.domain.User;
import com.miqu3iasg.banking.auth.service.ApiKeyService;
import com.miqu3iasg.banking.auth.service.ApiKeyService.ApiKeyCreationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "API Keys", description = "API key management endpoints for service account authentication")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    // Helper method to safely extract User from SecurityContext
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("No authentication found in context");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof User)) {
            throw new IllegalStateException("Expected User principal, but found: " + principal.getClass().getSimpleName());
        }

        return (User) principal;
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Create a new API key", description = "Creates a new API key for the authenticated user. The raw key is shown only once at creation time.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "API key created successfully", content = @Content(schema = @Schema(implementation = CreateApiKeyResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CreateApiKeyResponse> createApiKey(@RequestBody CreateApiKeyRequest request) {
        User user = getCurrentUser();

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
    @Operation(summary = "List API keys", description = "Lists all API keys for the authenticated user.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved list of API keys",
                   content = @Content(schema = @Schema(implementation = ApiKeySummaryResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ApiKeySummaryResponse>> listApiKeys() {
        User user = getCurrentUser();

        List<ApiKey> keys = apiKeyService.listApiKeys(user.getId());
        List<ApiKeySummaryResponse> response = keys.stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get API key details", description = "Retrieves details of a specific API key.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved API key details",
                   content = @Content(schema = @Schema(implementation = ApiKeySummaryResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "API key not found")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiKeySummaryResponse> getApiKey(@PathVariable UUID id) {
        User user = getCurrentUser();

        ApiKey key = apiKeyService.listApiKeys(user.getId()).stream()
                .filter(k -> k.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));

        return ResponseEntity.ok(toSummaryResponse(key));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Revoke an API key", description = "Revokes an API key, making it immediately invalid.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "API key successfully revoked"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "API key not found")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> revokeApiKey(
            @Parameter(description = "API key ID") @PathVariable UUID id,
            @Parameter(description = "Reason for revocation") @RequestParam String reason) {
        User user = getCurrentUser();

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
    @Operation(summary = "Rotate an API key", description = "Rotates an API key, generating a new key and optionally maintaining the old key for a grace period.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "API key successfully rotated",
                    content = @Content(schema = @Schema(implementation = RotateApiKeyResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "API key not found")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RotateApiKeyResponse> rotateApiKey(
            @Parameter(description = "API key ID") @PathVariable UUID id,
            @Parameter(description = "Grace period in days for the old key") @RequestParam int gracePeriodDays) {
        User user = getCurrentUser();
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