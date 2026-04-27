package com.miqu3iasg.banking_mvp.auth.apikey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.domain.Permission;
import com.miqu3iasg.banking.auth.domain.User;
import com.miqu3iasg.banking_mvp.auth.support.AbstractAuthIntegrationTest;
import com.miqu3iasg.banking_mvp.auth.support.AuthTestDataFactory;
import com.miqu3iasg.banking_mvp.auth.support.JwtTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyControllerIntegrationTest extends AbstractAuthIntegrationTest {

    @Test
    void createApiKey_returnsRawKeyOnce() throws Exception {
        User user = factory.createUser(AccountStatus.ACTIVE);
        String token = jwtHelper.generateValidToken(user.getId(), user.getEmail(), Set.of("ROLE_USER"));

        String requestBody = """
                {
                    "name": "Test API Key",
                    "description": "Integration test key",
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

        String rawKey = extractField(result.getResponseBody(), "rawKey");
        String keyPrefix = extractField(result.getResponseBody(), "keyPrefix");

        assertThat(rawKey).startsWith("bk_");
        assertThat(keyPrefix).startsWith("bk_");
        assertThat(rawKey).isNotEqualTo(keyPrefix);
    }

    @Test
    void listApiKeys_returnsUserKeys() throws Exception {
        User user = factory.createUser(AccountStatus.ACTIVE);
        String token = jwtHelper.generateValidToken(user.getId(), user.getEmail(), Set.of("ROLE_USER"));
        String rawKey = factory.createApiKey(EnumSet.of(Permission.TRANSACTION_READ));

        webTestClient.get()
                .uri("/api/v1/auth/api-keys")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].keyPrefix").value(prefix -> prefix.startsWith("bk_"));
    }

    @Test
    void getApiKey_returnsDetails() throws Exception {
        User user = factory.createUser(AccountStatus.ACTIVE);
        String token = jwtHelper.generateValidToken(user.getId(), user.getEmail(), Set.of("ROLE_USER"));
        String rawKey = factory.createApiKey(EnumSet.of(Permission.TRANSACTION_READ));

        User finalUser = user;
        ApiKey apiKey = factory.getApiKeyRepository().findByOwnerId(user.getId()).get(0);

        webTestClient.get()
                .uri("/api/v1/auth/api-keys/" + apiKey.getId())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.keyPrefix").value(prefix -> prefix.startsWith("bk_"))
                .jsonPath("$.name").value(name -> name != null);
    }

    private String extractField(byte[] json, String field) throws Exception {
        if (json == null) return null;
        JsonNode node = new ObjectMapper().readTree(json);
        return node.has(field) ? node.get(field).asText() : null;
    }
}
