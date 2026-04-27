package com.miqu3iasg.banking_mvp.auth.security;

import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.domain.User;
import com.miqu3iasg.banking_mvp.auth.support.AbstractAuthIntegrationTest;
import com.miqu3iasg.banking_mvp.auth.support.AuthTestDataFactory;
import com.miqu3iasg.banking_mvp.auth.support.JwtTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.ExchangeResult;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Security Infrastructure Tests")
class SecurityInfrastructureIntegrationTest extends AbstractAuthIntegrationTest {

    @Autowired
    private AuthTestDataFactory factory;

    @Autowired
    private JwtTestHelper jwtHelper;

    @Test
    void should_returnCorsHeaders_when_allowedOrigin() throws Exception {
        webTestClient.options().uri("/api/v1/auth/login")
            .header("Origin", "http://localhost:3000")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "Content-Type")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
    }

    @Test
    void should_notReturnCorsHeaders_when_disallowedOrigin() throws Exception {
        ExchangeResult result = webTestClient.options().uri("/api/v1/auth/login")
            .header("Origin", "http://evil.com")
            .header("Access-Control-Request-Method", "POST")
            .exchange()
            .returnResult();

        String allowOrigin = result.getResponseHeaders().getFirst("Access-Control-Allow-Origin");
        assertThat(allowOrigin)
            .as("CORS should not allow http://evil.com origin")
            .isNotEqualTo("http://evil.com");
    }

    @Test
    void should_notContainWildcardCorsHeaders() throws Exception {
        ExchangeResult result = webTestClient.options().uri("/api/v1/auth/login")
            .header("Origin", "http://localhost:3000")
            .header("Access-Control-Request-Method", "POST")
            .exchange()
            .returnResult();

        String allowOrigin = result.getResponseHeaders().getFirst("Access-Control-Allow-Origin");
        assertThat(allowOrigin)
            .as("CORS should not contain wildcard '*'")
            .isNotEqualTo("*");
    }

    @Test
    void should_includeSecurityHeaders_onAllResponses() throws Exception {
        User user = factory.createUser(AccountStatus.ACTIVE);

        EntityExchangeResult<byte[]> loginResult = webTestClient.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"email":"%s","password":"TestP@ssw0rd123!"}
                """.formatted(user.getEmail()))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .returnResult();

        assertThat(loginResult.getResponseHeaders().getFirst("Strict-Transport-Security"))
            .as("HSTS header must be present")
            .isNotNull();
        assertThat(loginResult.getResponseHeaders().getFirst("X-Frame-Options"))
            .as("X-Frame-Options header must be present")
            .isNotNull();
        assertThat(loginResult.getResponseHeaders().getFirst("X-Content-Type-Options"))
            .as("X-Content-Type-Options header must be present")
            .isNotNull();
    }

    @Test
    void should_return429_when_loginRateLimitExceeded() throws Exception {
        String email = factory.generateEmail();
        factory.createUser(AccountStatus.ACTIVE);

        for (int i = 0; i < 12; i++) {
            webTestClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {"email":"%s","password":"WrongP@ssw0rd123!"}
                    """.formatted(email))
                .exchange();
        }

        webTestClient.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"email":"%s","password":"TestP@ssw0rd123!"}
                """.formatted(email))
            .exchange()
            .expectStatus().isEqualTo(429);
    }

    @Test
    void should_return401_when_algNoneJwtSubmitted() throws Exception {
        String noneToken = jwtHelper.generateAlgNoneToken(
            java.util.UUID.randomUUID(), "test@test.com", Set.of("ROLE_USER"));

        webTestClient.get().uri("/api/v1/auth/me")
            .headers(h -> h.setBearerAuth(noneToken))
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void should_return401_when_hs256JwtSubmittedToRs256Decoder() throws Exception {
        String hs256Token = jwtHelper.generateHs256Token(
            java.util.UUID.randomUUID(), "test@test.com", Set.of("ROLE_USER"));

        webTestClient.get().uri("/api/v1/auth/me")
            .headers(h -> h.setBearerAuth(hs256Token))
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void should_return401_when_tamperedJwtSubmitted() throws Exception {
        String tamperedToken = jwtHelper.generateTamperedToken(
            java.util.UUID.randomUUID(), "test@test.com", Set.of("ROLE_USER"));

        webTestClient.get().uri("/api/v1/auth/me")
            .headers(h -> h.setBearerAuth(tamperedToken))
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void should_return401_when_expiredJwtSubmitted() throws Exception {
        String expiredToken = jwtHelper.generateExpiredToken(
            java.util.UUID.randomUUID(), "test@test.com", Set.of("ROLE_USER"));

        webTestClient.get().uri("/api/v1/auth/me")
            .headers(h -> h.setBearerAuth(expiredToken))
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void should_return401_when_inflatedRolesInJwt() throws Exception {
        String inflatedToken = jwtHelper.generateTokenWithInflatedRoles(
            java.util.UUID.randomUUID(), "test@test.com");

        webTestClient.get().uri("/api/v1/auth/me")
            .headers(h -> h.setBearerAuth(inflatedToken))
            .exchange()
            .expectStatus().isUnauthorized();
    }
}
