package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.LoginAttempt;
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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("auth-test")
@Testcontainers
class LoginAttemptRepositoryIT {

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
    private LoginAttemptRepository loginAttemptRepository;

    @Test
    void save_persistsLoginAttempt() {
        LoginAttempt attempt = createAttempt();

        LoginAttempt saved = loginAttemptRepository.save(attempt);

        LoginAttempt found = loginAttemptRepository.findById(saved.getId()).get();
        assertThat(found.isSuccess()).isTrue();
    }

    @Test
    void findByUserId_returnsAttempts() {
        UUID userId = UUID.randomUUID();
        createAndSaveAttempt(userId, "192.168.1.1");
        createAndSaveAttempt(userId, "192.168.1.1");

        var attempts = loginAttemptRepository.findByUserId(userId, PageRequest.of(0, 10));

        assertThat(attempts.getContent()).hasSize(2);
    }

    @Test
    void findByIpHash_returnsAttempts() {
        String ipHash = "iphash123";
        createAndSaveAttempt(UUID.randomUUID(), ipHash);
        createAndSaveAttempt(UUID.randomUUID(), ipHash);

        var attempts = loginAttemptRepository.findByIpHash(ipHash, PageRequest.of(0, 10));

        assertThat(attempts.getContent()).hasSize(2);
    }

    @Test
    void countByIpSince_returnsCount() {
        String ipHash = "iphash_count_" + UUID.randomUUID();
        createAndSaveAttempt(UUID.randomUUID(), ipHash, false, "INVALID_PASSWORD");
        createAndSaveAttempt(UUID.randomUUID(), ipHash, false, "INVALID_PASSWORD");
        createAndSaveAttempt(UUID.randomUUID(), ipHash, true, null);

        long count = loginAttemptRepository.countByIpSince(ipHash, Instant.now().minusSeconds(3600));

        assertThat(count).isEqualTo(3);
    }

    @Test
    void countSuccessfulLoginsByUserSince_returnsCount() {
        UUID userId = UUID.randomUUID();
        createAndSaveAttempt(userId, "192.168.1.1", true, null);
        createAndSaveAttempt(userId, "192.168.1.1", true, null);
        createAndSaveAttempt(userId, "192.168.1.1", false, "INVALID_PASSWORD");

        long count = loginAttemptRepository.countSuccessfulLoginsByUserSince(userId, Instant.now().minusSeconds(3600));

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countByUserIdSince_returnsCount() {
        UUID userId = UUID.randomUUID();
        createAndSaveAttempt(userId, "192.168.1.1");
        createAndSaveAttempt(userId, "192.168.1.2");

        long count = loginAttemptRepository.countByUserIdSince(userId, Instant.now().minusSeconds(3600));

        assertThat(count).isEqualTo(2);
    }

    private LoginAttempt createAttempt() {
        return createAttempt(UUID.randomUUID(), "192.168.1.1");
    }

    private LoginAttempt createAttempt(UUID userId, String ip) {
        return createAttempt(userId, ip, true, null);
    }

    private LoginAttempt createAttempt(UUID userId, String ip, boolean success, String failureReason) {
        return LoginAttempt.builder()
                .userId(userId)
                .emailHash("emailhash")
                .ipHash(ip)
                .userAgentHash("uahash")
                .success(success)
                .failureReason(failureReason)
                .lockedOut(false)
                .build();
    }

    private LoginAttempt createAndSaveAttempt(UUID userId, String ip) {
        return loginAttemptRepository.save(createAttempt(userId, ip));
    }

    private LoginAttempt createAndSaveAttempt(UUID userId, String ip, boolean success, String failureReason) {
        return loginAttemptRepository.save(createAttempt(userId, ip, success, failureReason));
    }
}
