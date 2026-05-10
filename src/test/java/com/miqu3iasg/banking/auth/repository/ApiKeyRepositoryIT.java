package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.ApiKey;
import com.miqu3iasg.banking.auth.domain.Permission;
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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("auth-test")
@Testcontainers
class ApiKeyRepositoryIT {

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
    private ApiKeyRepository apiKeyRepository;

    @Test
    void save_persistsApiKey() {
        ApiKey apiKey = createApiKey();

        ApiKey saved = apiKeyRepository.save(apiKey);

        Optional<ApiKey> found = apiKeyRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Key");
    }

    @Test
    void findByKeyHash_returnsApiKey() {
        ApiKey apiKey = createApiKey();
        apiKeyRepository.save(apiKey);

        Optional<ApiKey> found = apiKeyRepository.findByKeyHash(apiKey.getKeyHash());

        assertThat(found).isPresent();
        assertThat(found.get().getKeyHash()).isEqualTo(apiKey.getKeyHash());
    }

    @Test
    void findValidKey_returnsValidKey() {
        ApiKey apiKey = createApiKey();
        apiKeyRepository.save(apiKey);

        Optional<ApiKey> found = apiKeyRepository.findValidKey(apiKey.getKeyHash(), Instant.now());

        assertThat(found).isPresent();
    }

    @Test
    void findValidKey_whenRevoked_returnsEmpty() {
        ApiKey apiKey = createApiKey();
        apiKey.setRevoked(true);
        apiKeyRepository.save(apiKey);

        Optional<ApiKey> found = apiKeyRepository.findValidKey(apiKey.getKeyHash(), Instant.now());

        assertThat(found).isEmpty();
    }

    @Test
    void findValidKey_whenExpired_returnsEmpty() {
        ApiKey apiKey = createApiKey();
        apiKey.setExpiresAt(Instant.now().minusSeconds(3600));
        apiKeyRepository.save(apiKey);

        Optional<ApiKey> found = apiKeyRepository.findValidKey(apiKey.getKeyHash(), Instant.now());

        assertThat(found).isEmpty();
    }

    @Test
    void findByOwnerId_returnsKeys() {
        UUID ownerId = UUID.randomUUID();
        createAndSaveKey(ownerId, "key1");
        createAndSaveKey(ownerId, "key2");

        List<ApiKey> keys = apiKeyRepository.findByOwnerId(ownerId);

        assertThat(keys).hasSize(2);
    }

    @Test
    void findByKeyPrefix_returnsKeysWithPrefix() {
        createApiKey();

        List<ApiKey> keys = apiKeyRepository.findByKeyPrefix("test_");

        assertThat(keys).hasSize(1);
    }

    @Test
    void findByOwnerIdAndRevokedFalse_returnsActiveKeys() {
        UUID ownerId = UUID.randomUUID();
        createAndSaveKey(ownerId, "active1");
        createAndSaveKey(ownerId, "active2");
        ApiKey revoked = createAndSaveKey(ownerId, "revoked");
        revoked.setRevoked(true);
        apiKeyRepository.save(revoked);

        List<ApiKey> keys = apiKeyRepository.findByOwnerIdAndRevokedFalse(ownerId);

        assertThat(keys).hasSize(2);
    }

    @Test
    @Transactional
    void revokeAllByOwnerId_revokesKeys() {
        UUID ownerId = UUID.randomUUID();
        createAndSaveKey(ownerId, "key1");
        createAndSaveKey(ownerId, "key2");

        int revoked = apiKeyRepository.revokeAllByOwnerId(ownerId, Instant.now(), "TEST");

        assertThat(revoked).isEqualTo(2);
    }

    @Test
    @Transactional
    void findExpiredKeys_returnsExpiredKeys() {
        Instant now = Instant.now();
        String uniqueHash = "hash_expired_" + UUID.randomUUID();
        ApiKey expired = ApiKey.builder()
                .keyHash(uniqueHash)
                .keyPrefix("test_")
                .ownerId(UUID.randomUUID())
                .name("Expired Key")
                .description("Test API Key")
                .scopes(EnumSet.of(Permission.ACCOUNT_READ))
                .allowedIps(new HashSet<>())
                .expiresAt(now.minusSeconds(3600))
                .revoked(false)
                .build();
        apiKeyRepository.save(expired);

        List<ApiKey> expiredKeys = apiKeyRepository.findExpiredKeys(now);

        assertThat(expiredKeys).extracting(ApiKey::getKeyHash).contains(uniqueHash);
    }

    private ApiKey createApiKey() {
        return createApiKey(UUID.randomUUID(), "Test Key");
    }

    private ApiKey createApiKey(UUID ownerId, String name) {
        return ApiKey.builder()
                .keyHash("hash_" + UUID.randomUUID())
                .keyPrefix("test_")
                .ownerId(ownerId)
                .name(name)
                .description("Test API Key")
                .scopes(EnumSet.of(Permission.ACCOUNT_READ))
                .allowedIps(new HashSet<>())
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
    }

    private ApiKey createAndSaveKey(UUID ownerId, String name) {
        ApiKey apiKey = createApiKey(ownerId, name);
        return apiKeyRepository.save(apiKey);
    }
}
