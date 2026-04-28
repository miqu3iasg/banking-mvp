package com.miqu3iasg.banking.auth.config;

import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("auth-test")
@Testcontainers
class SecurityConfigIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("banking_auth_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("outbox.processor.enabled", () -> "false");
    }

    private WebTestClient webTestClient;
    private String validJwt;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        validJwt = jwtService.generateAccessToken(
                UUID.randomUUID(),
                "test@example.com",
                Set.of("USER"),
                Map.of(),
                AccountStatus.ACTIVE
        );
    }

    @Nested
    @DisplayName("Protected endpoints require JWT")
    class ProtectedEndpoints {

        @Test
        void accountsEndpoint_rejectsRequestWithoutJwt() {
            webTestClient.get().uri("/accounts/{id}", UUID.randomUUID())
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        void accountsEndpoint_acceptsRequestWithValidJwt() {
            var response = webTestClient.get().uri("/accounts/{id}", UUID.randomUUID())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                    .exchange()
                    .returnResult(Void.class);

            int status = response.getStatus().value();
            assertThat(status).isIn(200, 404);
        }

        @Test
        void transactionsEndpoint_rejectsRequestWithoutJwt() {
            webTestClient.post().uri("/transactions/deposit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        void transactionsEndpoint_acceptsRequestWithValidJwt() {
            var response = webTestClient.post().uri("/transactions/deposit")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "accountId": "%s",
                              "amount": 10.00,
                              "currency": "BRL"
                            }
                            """.formatted(UUID.randomUUID()))
                    .exchange()
                    .returnResult(Void.class);

            int status = response.getStatus().value();
            assertThat(status).isNotIn(401, 403);
        }
    }

    @Nested
    @DisplayName("Public endpoints accessible without JWT")
    class PublicEndpoints {

        @Test
        void actuatorHealth_isAccessibleWithoutAuth() {
            // Actuator may return 500 if Redis is down, but it should NOT return 401/403
            var response = webTestClient.get().uri("/actuator/health")
                    .exchange()
                    .returnResult(Void.class);

            int status = response.getStatus().value();
            assertThat(status).isNotIn(401, 403);
        }

        @Test
        void authLoginEndpoint_isAccessibleWithoutAuth() {
            // Auth login may return 401 for wrong credentials — that's the app,
            // not Spring Security blocking it. If Spring Security blocked it,
            // the response body would contain the SecurityErrorHandler format.
            // We just verify the endpoint is reachable (not 403 from security).
            var response = webTestClient.post().uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "email": "test@example.com",
                              "password": "wrongpassword"
                            }
                            """)
                    .exchange()
                    .returnResult(Void.class);

            int status = response.getStatus().value();
            assertThat(status).isNotEqualTo(403);
        }

        @Test
        void boletoWebhookEndpoint_isAccessibleWithoutAuth() {
            var response = webTestClient.post().uri("/v1/boleto/webhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .exchange()
                    .returnResult(Void.class);

            int status = response.getStatus().value();
            assertThat(status)
                    .as("Boleto webhook should be publicly accessible but got %d", status)
                    .isNotEqualTo(401);
        }

        @Test
        void pixWebhookEndpoint_isAccessibleWithoutAuth() {
            var response = webTestClient.post().uri("/v1/pix/webhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .exchange()
                    .returnResult(Void.class);

            int status = response.getStatus().value();
            assertThat(status)
                    .as("PIX webhook should be publicly accessible but got %d", status)
                    .isNotEqualTo(401);
        }
    }

    @Nested
    @DisplayName("JWT token validation")
    class JwtValidation {

        @Test
        void invalidJwt_isRejected() {
            webTestClient.get().uri("/accounts/{id}", UUID.randomUUID())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        void missingBearerPrefix_isRejected() {
            webTestClient.get().uri("/accounts/{id}", UUID.randomUUID())
                    .header(HttpHeaders.AUTHORIZATION, validJwt)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        void emptyBearerToken_isRejected() {
            webTestClient.get().uri("/accounts/{id}", UUID.randomUUID())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }
}
