package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.AuditLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("auth-test")
@Testcontainers
class AuditLogRepositoryIT {

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
    private AuditLogRepository auditLogRepository;

    @Test
    @Transactional
    void save_persistsAuditLog() {
        AuditLog log = createLog();

        AuditLog saved = auditLogRepository.save(log);

        AuditLog found = auditLogRepository.findById(saved.getId()).get();
        assertThat(found.getEventType()).isEqualTo("LOGIN_SUCCESS");
    }

    @Test
    @Transactional
    void findByUserId_returnsLogs() {
        UUID userId = UUID.randomUUID();
        createAndSaveLog(userId, "LOGIN_SUCCESS");
        createAndSaveLog(userId, "LOGOUT");

        var logs = auditLogRepository.findByUserId(userId, PageRequest.of(0, 10));

        assertThat(logs.getContent()).hasSize(2);
    }

    @Test
    @Transactional
    void findByEventType_returnsLogs() {
        createAndSaveLog(UUID.randomUUID(), "LOGIN_SUCCESS");
        createAndSaveLog(UUID.randomUUID(), "LOGIN_SUCCESS");

        var logs = auditLogRepository.findByEventType("LOGIN_SUCCESS", PageRequest.of(0, 10));

        assertThat(logs.getContent()).hasSize(2);
    }

    @Test
    @Transactional
    void findByOutcome_returnsLogs() {
        createAndSaveLog(UUID.randomUUID(), "LOGIN_SUCCESS");
        createAndSaveLog(UUID.randomUUID(), "LOGIN_SUCCESS");

        var logs = auditLogRepository.findByOutcome("SUCCESS", PageRequest.of(0, 10));

        assertThat(logs.getContent()).hasSize(2);
    }

    @Test
    @Transactional
    void findByTraceId_returnsLogs() {
        String traceId = "trace_" + UUID.randomUUID();
        createAndSaveLog(UUID.randomUUID(), "LOGIN_SUCCESS", traceId);

        List<AuditLog> logs = auditLogRepository.findByTraceId(traceId);

        assertThat(logs).hasSize(1);
    }

    @Test
    @Transactional
    void countByEventTypeAndOutcomeSince_returnsCount() {
        createAndSaveLog(UUID.randomUUID(), "LOGIN_SUCCESS");
        createAndSaveLog(UUID.randomUUID(), "LOGIN_SUCCESS");

        long count = auditLogRepository.countByEventTypeAndOutcomeSince("LOGIN_SUCCESS", "SUCCESS", Instant.now().minusSeconds(3600));

        assertThat(count).isEqualTo(2);
    }

    @Test
    @Transactional
    void findByUserIdAndDateRange_returnsLogs() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        createAndSaveLog(userId, "LOGIN_SUCCESS");
        createAndSaveLog(userId, "LOGOUT");

        List<AuditLog> logs = auditLogRepository.findByUserIdAndDateRange(userId, now.minusSeconds(3600), now.plusSeconds(3600));

        assertThat(logs).hasSize(2);
    }

    @Test
    @Transactional
    void findRecentByIpHash_returnsLogs() {
        String ipHash = "iphash_" + UUID.randomUUID();
        createAndSaveLog(UUID.randomUUID(), "LOGIN_SUCCESS", ipHash);
        createAndSaveLog(UUID.randomUUID(), "LOGIN_SUCCESS", ipHash);

        var logs = auditLogRepository.findRecentByIpHash(ipHash, Instant.now().minusSeconds(3600), PageRequest.of(0, 10));

        assertThat(logs.getContent()).hasSize(2);
    }

    private AuditLog createLog() {
        return createLog(UUID.randomUUID(), "LOGIN_SUCCESS");
    }

    private AuditLog createLog(UUID userId, String eventType) {
        return createLog(userId, eventType, "trace_" + UUID.randomUUID());
    }

    private AuditLog createLog(UUID userId, String eventType, String traceId) {
        return AuditLog.builder()
                .userId(userId)
                .eventType(eventType)
                .outcome("SUCCESS")
                .ipHash(traceId)
                .userAgentHash("uahash")
                .traceId(traceId)
                .mfaRequired(false)
                .mfaCompleted(false)
                .build();
    }

    private AuditLog createAndSaveLog(UUID userId, String eventType) {
        return auditLogRepository.save(createLog(userId, eventType));
    }

    private AuditLog createAndSaveLog(UUID userId, String eventType, String traceId) {
        return auditLogRepository.save(createLog(userId, eventType, traceId));
    }
}
