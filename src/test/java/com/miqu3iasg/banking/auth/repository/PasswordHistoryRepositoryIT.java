package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.PasswordHistory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("auth-test")
@Testcontainers
class PasswordHistoryRepositoryIT {

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
    private PasswordHistoryRepository passwordHistoryRepository;

    @Test
    void save_persistsPasswordHistory() {
        PasswordHistory history = createHistory();

        PasswordHistory saved = passwordHistoryRepository.save(history);

        PasswordHistory found = passwordHistoryRepository.findById(saved.getId()).get();
        assertThat(found.getUserId()).isEqualTo(history.getUserId());
        assertThat(found.getPasswordHash()).isEqualTo(history.getPasswordHash());
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc_returnsHistory() {
        UUID userId = UUID.randomUUID();
        createAndSaveHistory(userId);
        createAndSaveHistory(userId);

        List<PasswordHistory> history = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);

        assertThat(history).hasSize(2);
    }

    @Test
    void findRecentByUserId_returnsLimitedHistory() {
        UUID userId = UUID.randomUUID();
        createAndSaveHistory(userId);
        createAndSaveHistory(userId);
        createAndSaveHistory(userId);

        List<PasswordHistory> history = passwordHistoryRepository.findRecentByUserId(userId, 2);

        assertThat(history).hasSize(2);
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc_returnsPagedHistory() {
        UUID userId = UUID.randomUUID();
        createAndSaveHistory(userId);
        createAndSaveHistory(userId);
        createAndSaveHistory(userId);

        var page = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    private PasswordHistory createHistory() {
        return createHistory(UUID.randomUUID());
    }

    private PasswordHistory createHistory(UUID userId) {
        return PasswordHistory.builder()
                .userId(userId)
                .passwordHash("bcrypt_hash_" + UUID.randomUUID())
                .build();
    }

    private PasswordHistory createAndSaveHistory(UUID userId) {
        return passwordHistoryRepository.save(createHistory(userId));
    }
}
