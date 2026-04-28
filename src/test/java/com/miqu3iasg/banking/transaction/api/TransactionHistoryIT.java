package com.miqu3iasg.banking.transaction.api;

import com.miqu3iasg.banking.account.api.dto.CreateAccountRequest;
import com.miqu3iasg.banking.account.domain.AccountType;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.security.JwtService;
import com.miqu3iasg.banking.transaction.api.dto.DepositRequest;
import com.miqu3iasg.banking.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("auth-test")
@Testcontainers
class TransactionHistoryIT {

    private static final String BRL = "BRL";
    private static final BigDecimal AMOUNT_100 = new BigDecimal("100.00");
    private static final BigDecimal AMOUNT_200 = new BigDecimal("200.00");
    private static final String CPF_1 = "529.982.247-25";

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("banking_txn_test")
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
    private UUID accountId;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        transactionRepository.deleteAll();
        accountRepository.deleteAll();

        validJwt = jwtService.generateAccessToken(
                UUID.randomUUID(),
                "test@example.com",
                Set.of("USER"),
                Map.of(),
                AccountStatus.ACTIVE
        );

        // Create account via HTTP API
        accountId = webTestClient.post().uri("/accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(new CreateAccountRequest("John Doe", CPF_1, AccountType.CHECKING, "test@example.com"))
                .exchange()
                .returnResult(com.miqu3iasg.banking.account.api.dto.AccountResponse.class)
                .getResponseBody()
                .blockFirst()
                .id();
    }

    @Nested
    @DisplayName("GET /accounts/{accountId}/transactions")
    class GetTransactionHistory {

        @Test
        void returnsEmptyPageWhenNoTransactions() {
            webTestClient.get().uri("/accounts/{accountId}/transactions", accountId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.content.length()").isEqualTo(0)
                    .jsonPath("$.totalElements").isEqualTo(0);
        }

        @Test
        void supportsPaginationWithPageAndSizeParams() {
            // Make two deposits to have multiple transactions
            webTestClient.post().uri("/transactions/deposit")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(new DepositRequest(accountId, AMOUNT_100, BRL, null))
                    .exchange()
                    .expectStatus().isCreated();

            webTestClient.post().uri("/transactions/deposit")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(new DepositRequest(accountId, AMOUNT_200, BRL, null))
                    .exchange()
                    .expectStatus().isCreated();

            webTestClient.get().uri("/accounts/{accountId}/transactions?page=0&size=1", accountId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1)
                    .jsonPath("$.totalElements").isEqualTo(2)
                    .jsonPath("$.size").isEqualTo(1);
        }

        @Test
        void supportsDateRangeFiltering() {
            webTestClient.post().uri("/transactions/deposit")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(new DepositRequest(accountId, AMOUNT_100, BRL, null))
                    .exchange()
                    .expectStatus().isCreated();

            var now = java.time.Instant.now().minusSeconds(60);
            var future = java.time.Instant.now().plusSeconds(3600);

            webTestClient.get().uri(uriBuilder -> uriBuilder
                            .path("/accounts/{accountId}/transactions")
                            .queryParam("from", now.toString())
                            .queryParam("to", future.toString())
                            .build(accountId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1);
        }

        @Test
        void supportsTransactionTypeFiltering() {
            webTestClient.post().uri("/transactions/deposit")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(new DepositRequest(accountId, AMOUNT_100, BRL, null))
                    .exchange()
                    .expectStatus().isCreated();

            webTestClient.get().uri(uriBuilder -> uriBuilder
                            .path("/accounts/{accountId}/transactions")
                            .queryParam("type", "CREDIT")
                            .build(accountId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1);
        }

        @Test
        void returns404ForNonexistentAccount() {
            var unknownId = UUID.randomUUID();
            webTestClient.get().uri("/accounts/{accountId}/transactions", unknownId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }
}
