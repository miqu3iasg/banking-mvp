package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.EmailVerificationToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("auth-test")
@Testcontainers
class EmailVerificationTokenRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("banking_test")
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

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Test
    @Transactional
    void save_persistsEmailVerificationToken() {
        EmailVerificationToken token = createToken();

        EmailVerificationToken saved = emailVerificationTokenRepository.save(token);

        Optional<EmailVerificationToken> found = emailVerificationTokenRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(token.getUserId());
    }

    @Test
    @Transactional
    void findByTokenHash_returnsToken() {
        String tokenHash = "hash_" + UUID.randomUUID();
        EmailVerificationToken token = createToken();
        token.setTokenHash(tokenHash);
        emailVerificationTokenRepository.save(token);

        Optional<EmailVerificationToken> found = emailVerificationTokenRepository.findByTokenHash(tokenHash);

        assertThat(found).isPresent();
        assertThat(found.get().getTokenHash()).isEqualTo(tokenHash);
    }

    @Test
    @Transactional
    void findByTokenHash_whenNotFound_returnsEmpty() {
        Optional<EmailVerificationToken> found = emailVerificationTokenRepository.findByTokenHash("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    @Transactional
    void findValidToken_returnsValidToken() {
        EmailVerificationToken token = createToken();
        emailVerificationTokenRepository.save(token);

        Optional<EmailVerificationToken> found = emailVerificationTokenRepository.findValidToken(token.getTokenHash(), Instant.now());

        assertThat(found).isPresent();
        assertThat(found.get().isConsumed()).isFalse();
    }

    @Test
    @Transactional
    void findValidToken_whenConsumed_returnsEmpty() {
        EmailVerificationToken token = createToken();
        token.setConsumed(true);
        emailVerificationTokenRepository.save(token);

        Optional<EmailVerificationToken> found = emailVerificationTokenRepository.findValidToken(token.getTokenHash(), Instant.now());

        assertThat(found).isEmpty();
    }

    @Test
    @Transactional
    void findValidToken_whenExpired_returnsEmpty() {
        EmailVerificationToken token = createToken();
        token.setExpiresAt(Instant.now().minusSeconds(3600));
        emailVerificationTokenRepository.save(token);

        Optional<EmailVerificationToken> found = emailVerificationTokenRepository.findValidToken(token.getTokenHash(), Instant.now());

        assertThat(found).isEmpty();
    }

    @Test
    @Transactional
    void consumeAllTokensForUser_consumesTokens() {
        UUID userId = UUID.randomUUID();
        createAndSaveToken(userId);
        createAndSaveToken(userId);

        int consumed = emailVerificationTokenRepository.consumeAllTokensForUser(userId, Instant.now());

        assertThat(consumed).isEqualTo(2);
    }

    @Test
    @Transactional
    void deleteExpiredTokens_deletesExpired() {
        Instant now = Instant.now();

        // Create exactly one expired token
        EmailVerificationToken expired = EmailVerificationToken.builder()
                .tokenHash("hash_expired_" + UUID.randomUUID())
                .userId(UUID.randomUUID())
                .emailHash("emailhash_expired")
                .expiresAt(now.minusSeconds(3600))
                .consumed(false)
                .build();
        emailVerificationTokenRepository.save(expired);

        // Create exactly one valid token
        EmailVerificationToken valid = EmailVerificationToken.builder()
                .tokenHash("hash_valid_" + UUID.randomUUID())
                .userId(UUID.randomUUID())
                .emailHash("emailhash_valid")
                .expiresAt(now.plusSeconds(3600))
                .consumed(false)
                .build();
        emailVerificationTokenRepository.save(valid);

        int deleted = emailVerificationTokenRepository.deleteExpiredTokens(now);

        assertThat(deleted).isEqualTo(1);

        // Verify valid token still exists
        assertThat(emailVerificationTokenRepository.findById(valid.getId())).isPresent();
    }

    private EmailVerificationToken createToken() {
        return EmailVerificationToken.builder()
                .tokenHash("hash_" + UUID.randomUUID())
                .userId(UUID.randomUUID())
                .emailHash("emailhash_" + UUID.randomUUID())
                .expiresAt(Instant.now().plusSeconds(3600))
                .consumed(false)
                .build();
    }

    private EmailVerificationToken createAndSaveToken(UUID userId) {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .tokenHash("hash_" + UUID.randomUUID())
                .userId(userId)
                .emailHash("emailhash_" + UUID.randomUUID())
                .expiresAt(Instant.now().plusSeconds(3600))
                .consumed(false)
                .build();
        return emailVerificationTokenRepository.save(token);
    }
}
