package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.RefreshToken;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("auth-test")
@Testcontainers
class RefreshTokenRepositoryIT {

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
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void save_persistsRefreshToken() {
        RefreshToken token = createToken();

        RefreshToken saved = refreshTokenRepository.save(token);

        Optional<RefreshToken> found = refreshTokenRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(token.getUserId());
    }

    @Test
    void findByTokenHash_returnsToken() {
        String tokenHash = "hash_" + UUID.randomUUID();
        RefreshToken token = createToken();
        token.setTokenHash(tokenHash);
        refreshTokenRepository.save(token);

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(tokenHash);

        assertThat(found).isPresent();
        assertThat(found.get().getTokenHash()).isEqualTo(tokenHash);
    }

    @Test
    void findByTokenHash_whenNotFound_returnsEmpty() {
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    void findValidToken_returnsValidToken() {
        RefreshToken token = createToken();
        refreshTokenRepository.save(token);

        Optional<RefreshToken> found = refreshTokenRepository.findValidToken(token.getTokenHash(), Instant.now());

        assertThat(found).isPresent();
    }

    @Test
    void findValidToken_whenExpired_returnsEmpty() {
        RefreshToken token = createToken();
        token.setExpiresAt(Instant.now().minusSeconds(3600));
        refreshTokenRepository.save(token);

        Optional<RefreshToken> found = refreshTokenRepository.findValidToken(token.getTokenHash(), Instant.now());

        assertThat(found).isEmpty();
    }

    @Test
    void findByUserIdAndRevokedFalse_returnsActiveTokens() {
        UUID userId = UUID.randomUUID();
        createAndSaveToken(userId);
        createAndSaveToken(userId);

        List<RefreshToken> tokens = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);

        assertThat(tokens).hasSize(2);
    }

    @Test
    void findByFamilyId_returnsTokens() {
        UUID familyId = UUID.randomUUID();
        createAndSaveTokenWithFamily(familyId);
        createAndSaveTokenWithFamily(familyId);

        List<RefreshToken> tokens = refreshTokenRepository.findByFamilyId(familyId);

        assertThat(tokens).hasSize(2);
    }

    @Test
    void countActiveTokensByUser_returnsCount() {
        UUID userId = UUID.randomUUID();
        createAndSaveToken(userId);
        createAndSaveToken(userId);

        long count = refreshTokenRepository.countActiveTokensByUser(userId, Instant.now());

        assertThat(count).isEqualTo(2);
    }

    @Test
    @Transactional
    void revokeAllUserTokens_revokesTokens() {
        UUID userId = UUID.randomUUID();
        createAndSaveToken(userId);
        createAndSaveToken(userId);

        int revoked = refreshTokenRepository.revokeAllUserTokens(userId, Instant.now(), "TEST");

        assertThat(revoked).isEqualTo(2);
    }

    @Test
    @Transactional
    void revokeFamilyTokens_revokesTokens() {
        UUID familyId = UUID.randomUUID();
        createAndSaveTokenWithFamily(familyId);

        int revoked = refreshTokenRepository.revokeFamilyTokens(familyId, Instant.now(), "TEST");

        assertThat(revoked).isEqualTo(1);
    }

    private RefreshToken createToken() {
        return RefreshToken.builder()
                .tokenHash("hash_" + UUID.randomUUID())
                .userId(UUID.randomUUID())
                .familyId(UUID.randomUUID())
                .deviceFingerprint("test-device")
                .ipHash("iphash")
                .userAgentHash("uahash")
                .revoked(false)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private RefreshToken createAndSaveToken(UUID userId) {
        RefreshToken token = RefreshToken.builder()
                .tokenHash("hash_" + UUID.randomUUID())
                .userId(userId)
                .familyId(UUID.randomUUID())
                .deviceFingerprint("test-device")
                .ipHash("iphash")
                .userAgentHash("uahash")
                .revoked(false)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return refreshTokenRepository.save(token);
    }

    private RefreshToken createAndSaveTokenWithFamily(UUID familyId) {
        RefreshToken token = RefreshToken.builder()
                .tokenHash("hash_" + UUID.randomUUID())
                .userId(UUID.randomUUID())
                .familyId(familyId)
                .deviceFingerprint("test-device")
                .ipHash("iphash")
                .userAgentHash("uahash")
                .revoked(false)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return refreshTokenRepository.save(token);
    }
}
