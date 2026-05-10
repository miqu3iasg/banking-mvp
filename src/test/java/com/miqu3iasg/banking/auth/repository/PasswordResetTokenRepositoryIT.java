package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.PasswordResetToken;
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
class PasswordResetTokenRepositoryIT {

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
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    @Transactional
    void save_persistsPasswordResetToken() {
        PasswordResetToken token = createToken();

        PasswordResetToken saved = passwordResetTokenRepository.save(token);

        Optional<PasswordResetToken> found = passwordResetTokenRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTokenHash()).isEqualTo(token.getTokenHash());
    }

    @Test
    @Transactional
    void findByTokenHash_returnsToken() {
        String tokenHash = "hash_" + UUID.randomUUID();
        PasswordResetToken token = createToken();
        token.setTokenHash(tokenHash);
        passwordResetTokenRepository.save(token);

        Optional<PasswordResetToken> found = passwordResetTokenRepository.findByTokenHash(tokenHash);

        assertThat(found).isPresent();
        assertThat(found.get().getTokenHash()).isEqualTo(tokenHash);
    }

    @Test
    @Transactional
    void findByTokenHash_whenNotFound_returnsEmpty() {
        Optional<PasswordResetToken> found = passwordResetTokenRepository.findByTokenHash("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    @Transactional
    void findValidToken_returnsValidToken() {
        PasswordResetToken token = createToken();
        passwordResetTokenRepository.save(token);

        Optional<PasswordResetToken> found = passwordResetTokenRepository.findValidToken(token.getTokenHash(), Instant.now());

        assertThat(found).isPresent();
    }

    @Test
    @Transactional
    void findValidToken_whenConsumed_returnsEmpty() {
        PasswordResetToken token = createToken();
        token.setConsumed(true);
        passwordResetTokenRepository.save(token);

        Optional<PasswordResetToken> found = passwordResetTokenRepository.findValidToken(token.getTokenHash(), Instant.now());

        assertThat(found).isEmpty();
    }

    @Test
    @Transactional
    void findValidToken_whenExpired_returnsEmpty() {
        PasswordResetToken token = createToken();
        token.setExpiresAt(Instant.now().minusSeconds(3600));
        passwordResetTokenRepository.save(token);

        Optional<PasswordResetToken> found = passwordResetTokenRepository.findValidToken(token.getTokenHash(), Instant.now());

        assertThat(found).isEmpty();
    }

    @Test
    @Transactional
    void consumeAllTokensForUser_consumesTokens() {
        UUID userId = UUID.randomUUID();
        createAndSaveToken(userId);
        createAndSaveToken(userId);

        int consumed = passwordResetTokenRepository.consumeAllTokensForUser(userId, Instant.now());

        assertThat(consumed).isEqualTo(2);
    }

    @Test
    @Transactional
    void deleteExpiredTokens_deletesExpired() {
        Instant now = Instant.now();

        // Create exactly one expired token
        PasswordResetToken expired = PasswordResetToken.builder()
                .tokenHash("hash_expired_" + UUID.randomUUID())
                .userId(UUID.randomUUID())
                .expiresAt(now.minusSeconds(3600))
                .consumed(false)
                .build();
        passwordResetTokenRepository.save(expired);

        // Create exactly one valid token
        PasswordResetToken valid = PasswordResetToken.builder()
                .tokenHash("hash_valid_" + UUID.randomUUID())
                .userId(UUID.randomUUID())
                .expiresAt(now.plusSeconds(3600))
                .consumed(false)
                .build();
        passwordResetTokenRepository.save(valid);

        int deleted = passwordResetTokenRepository.deleteExpiredTokens(now);

        assertThat(deleted).isEqualTo(1);

        // Verify valid token still exists
        assertThat(passwordResetTokenRepository.findById(valid.getId())).isPresent();
    }

    private PasswordResetToken createToken() {
        return PasswordResetToken.builder()
                .tokenHash("hash_" + UUID.randomUUID())
                .userId(UUID.randomUUID())
                .expiresAt(Instant.now().plusSeconds(3600))
                .consumed(false)
                .build();
    }

    private PasswordResetToken createAndSaveToken(UUID userId) {
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash("hash_" + UUID.randomUUID())
                .userId(userId)
                .expiresAt(Instant.now().plusSeconds(3600))
                .consumed(false)
                .build();
        return passwordResetTokenRepository.save(token);
    }
}
