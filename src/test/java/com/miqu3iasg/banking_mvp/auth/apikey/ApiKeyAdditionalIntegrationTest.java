package com.miqu3iasg.banking_mvp.auth.apikey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.domain.Permission;
import com.miqu3iasg.banking.auth.domain.User;
import com.miqu3iasg.banking_mvp.auth.support.AbstractAuthIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAdditionalIntegrationTest extends AbstractAuthIntegrationTest {

    @Test
    void apiKeyWithScope_canAccessScopedEndpoint() throws Exception {
        User user = factory.createUser(AccountStatus.ACTIVE);
        String token = jwtHelper.generateValidToken(user.getId(), user.getEmail(), Set.of("ROLE_USER"));

        String requestBody = """
                {
                    "name": "Scoped API Key",
                    "description": "API key with specific scopes",
                    "scopes": ["TRANSACTION_READ"]
                }
                """;

        EntityExchangeResult<byte[]> result = webTestClient.post()
                .uri("/api/v1/auth/api-keys")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .returnResult();

        String keyId = extractField(result.getResponseBody(), "id");

        // Test that we can retrieve the key details
        webTestClient.get()
                .uri("/api/v1/auth/api-keys/" + keyId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.keyPrefix").value(prefix -> assertThat(prefix).asString().startsWith("bk_"));
    }

    @Test
    void apiKeyRotationWithGracePeriod() throws Exception {
        User user = factory.createUser(AccountStatus.ACTIVE);
        String token = jwtHelper.generateValidToken(user.getId(), user.getEmail(), Set.of("ROLE_USER"));

        // Create an API key first
        String createRequest = """
                {
                    "name": "Original Key",
                    "description": "Key to rotate"
                }
                """;

        EntityExchangeResult<byte[]> createResult = webTestClient.post()
                .uri("/api/v1/auth/api-keys")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRequest)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .returnResult();

        String keyId = extractField(createResult.getResponseBody(), "id");

        // Rotate the key with 7-day grace period
        webTestClient.post()
                .uri("/api/v1/auth/api-keys/" + keyId + "/rotate?gracePeriodDays=7")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.rawKey").exists()
                .jsonPath("$.keyPrefix").exists();
    }

    private String extractField(byte[] json, String field) throws Exception {
        if (json == null) return null;
        JsonNode node = new ObjectMapper().readTree(json);
        return node.has(field) ? node.get(field).asText() : null;
    }
}