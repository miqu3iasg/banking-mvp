package com.miqu3iasg.banking.auth.service;

import com.miqu3iasg.banking.auth.repository.AuditLogRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.time.Duration;

@SpringBootTest
@ActiveProfiles("auth-test")
@Testcontainers
class AuditLogServiceIT {

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
  private AuditLogService auditLogService;

  @Autowired
  private AuditLogRepository auditLogRepository;

  @Test
  void logLoginAttempt_whenSuccess_thenLogsAudit() {
    UUID userId = UUID.randomUUID();
    String emailHash = "email_hash";
    String ipHash = "ip_hash";
    String userAgentHash = "ua_hash";

    auditLogService.logLoginAttempt(userId, emailHash, ipHash, userAgentHash, "SUCCESS", null);

    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          var logs = auditLogRepository.findByUserId(userId, PageRequest.of(0, 10));
          assertThat(logs.getContent()).hasSize(1);
          assertThat(logs.getContent().get(0).getEventType()).isEqualTo("LOGIN");
          assertThat(logs.getContent().get(0).getOutcome()).isEqualTo("SUCCESS");
        });
  }

  @Test
  void logLoginAttempt_whenFailure_thenLogsAudit() {
    UUID userId = UUID.randomUUID();
    String emailHash = "email_hash";
    String ipHash = "ip_hash";
    String userAgentHash = "ua_hash";
    String failureReason = "INVALID_PASSWORD";

    auditLogService.logLoginAttempt(userId, emailHash, ipHash, userAgentHash, "FAILURE", failureReason);

    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          var logs = auditLogRepository.findByUserId(userId, PageRequest.of(0, 10));
          assertThat(logs.getContent()).hasSize(1);
          assertThat(logs.getContent().get(0).getEventType()).isEqualTo("LOGIN");
          assertThat(logs.getContent().get(0).getOutcome()).isEqualTo("FAILURE");
          assertThat(logs.getContent().get(0).getFailureReason()).isEqualTo(failureReason);
        });
  }

  @Test
  void logRegistration_whenValid_thenLogsAudit() {
    UUID userId = UUID.randomUUID();
    String email = "test@example.com";
    String ipHash = "ip_hash";

    auditLogService.logRegistration(userId, email, ipHash, "SUCCESS");

    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          var logs = auditLogRepository.findByUserId(userId, PageRequest.of(0, 10));
          assertThat(logs.getContent()).hasSize(1);
          assertThat(logs.getContent().get(0).getEventType()).isEqualTo("REGISTRATION");
        });
  }

  @Test
  void logTokenRefresh_whenSuccess_thenLogsAudit() {
    UUID userId = UUID.randomUUID();

    auditLogService.logTokenRefresh(userId, "SUCCESS", null);

    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          var logs = auditLogRepository.findByUserId(userId, PageRequest.of(0, 10));
          assertThat(logs.getContent()).hasSize(1);
          assertThat(logs.getContent().get(0).getEventType()).isEqualTo("TOKEN_REFRESH");
        });
  }

  @Test
  void logTokenRefresh_whenFailure_thenLogsAudit() {
    UUID userId = UUID.randomUUID();

    auditLogService.logTokenRefresh(userId, "FAILURE", "TOKEN_EXPIRED");

    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          var logs = auditLogRepository.findByUserId(userId, PageRequest.of(0, 10));
          assertThat(logs.getContent()).hasSize(1);
          assertThat(logs.getContent().get(0).getEventType()).isEqualTo("TOKEN_REFRESH");
          assertThat(logs.getContent().get(0).getOutcome()).isEqualTo("FAILURE");
        });
  }

  @Test
  void logPasswordChange_whenValid_thenLogsAudit() {
    UUID userId = UUID.randomUUID();

    auditLogService.logPasswordChange(userId, "ip_hash", "SUCCESS");

    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          var logs = auditLogRepository.findByUserId(userId, PageRequest.of(0, 10));
          assertThat(logs.getContent()).hasSize(1);
          assertThat(logs.getContent().get(0).getEventType()).isEqualTo("PASSWORD_CHANGE");
        });
  }

  @Test
  void logMfaAttempt_whenValid_thenLogsAudit() {
    UUID userId = UUID.randomUUID();
    String ipHash = "ip_hash";

    auditLogService.logMfaAttempt(userId, ipHash, "SUCCESS", null);

    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          var logs = auditLogRepository.findByUserId(userId, PageRequest.of(0, 10));
          assertThat(logs.getContent()).hasSize(1);
          assertThat(logs.getContent().get(0).getEventType()).isEqualTo("MFA_ATTEMPT");
        });
  }

  @Test
  void logAdminAction_whenValid_thenLogsAudit() {
    UUID userId = UUID.randomUUID();
    String adminId = "admin_" + UUID.randomUUID();

    auditLogService.logAdminAction(userId, "API_KEY_CREATED", "ADMIN_ACTION", adminId, "SUCCESS");

    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          var logs = auditLogRepository.findByUserId(userId, PageRequest.of(0, 10));
          assertThat(logs.getContent()).hasSize(1);
          assertThat(logs.getContent().get(0).getEventType()).isEqualTo("API_KEY_CREATED");
        });
  }
}
