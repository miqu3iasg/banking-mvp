package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.MfaBackupCode;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("auth-test")
@Testcontainers
class MfaBackupCodeRepositoryIT {

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
    private MfaBackupCodeRepository mfaBackupCodeRepository;

    @Test
    @Transactional
    void save_persistsBackupCode() {
        MfaBackupCode backupCode = createBackupCode();

        MfaBackupCode saved = mfaBackupCodeRepository.save(backupCode);

        Optional<MfaBackupCode> found = mfaBackupCodeRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(backupCode.getUserId());
    }

    @Test
    @Transactional
    void findByCodeHash_returnsBackupCode() {
        String codeHash = "hash_" + UUID.randomUUID();
        MfaBackupCode backupCode = createBackupCode();
        backupCode.setCodeHash(codeHash);
        mfaBackupCodeRepository.save(backupCode);

        Optional<MfaBackupCode> found = mfaBackupCodeRepository.findByCodeHash(codeHash);

        assertThat(found).isPresent();
        assertThat(found.get().getCodeHash()).isEqualTo(codeHash);
    }

    @Test
    @Transactional
    void findByCodeHashAndUsedFalse_returnsUnusedCode() {
        MfaBackupCode backupCode = createBackupCode();
        mfaBackupCodeRepository.save(backupCode);

        Optional<MfaBackupCode> found = mfaBackupCodeRepository.findByCodeHashAndUsedFalse(backupCode.getCodeHash());

        assertThat(found).isPresent();
        assertThat(found.get().isUsed()).isFalse();
    }

    @Test
    @Transactional
    void findByCodeHashAndUsedFalse_whenUsed_returnsEmpty() {
        MfaBackupCode backupCode = createBackupCode();
        backupCode.setUsed(true);
        mfaBackupCodeRepository.save(backupCode);

        Optional<MfaBackupCode> found = mfaBackupCodeRepository.findByCodeHashAndUsedFalse(backupCode.getCodeHash());

        assertThat(found).isEmpty();
    }

    @Test
    @Transactional
    void countByUserIdAndUsedFalse_returnsCount() {
        UUID userId = UUID.randomUUID();
        createAndSaveBackupCode(userId, false);
        createAndSaveBackupCode(userId, false);
        createAndSaveBackupCode(userId, true);

        long count = mfaBackupCodeRepository.countByUserIdAndUsedFalse(userId);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @Transactional
    void countByUserIdAndUsedFalse_whenAllUsed_returnsZero() {
        UUID userId = UUID.randomUUID();
        createAndSaveBackupCode(userId, true);

        long count = mfaBackupCodeRepository.countByUserIdAndUsedFalse(userId);

        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void findByUserIdAndUsedFalse_returnsUnusedCodes() {
        UUID userId = UUID.randomUUID();
        createAndSaveBackupCode(userId, false);
        createAndSaveBackupCode(userId, false);

        var codes = mfaBackupCodeRepository.findByUserIdAndUsedFalse(userId);

        assertThat(codes).hasSize(2);
    }

    @Test
    @Transactional
    void deleteByUserId_deletesCodes() {
        UUID userId = UUID.randomUUID();
        createAndSaveBackupCode(userId, false);
        createAndSaveBackupCode(userId, false);

        mfaBackupCodeRepository.deleteByUserId(userId);

        assertThat(mfaBackupCodeRepository.findAll()).isEmpty();
    }

    private MfaBackupCode createBackupCode() {
        return createBackupCode(UUID.randomUUID(), false);
    }

    private MfaBackupCode createBackupCode(UUID userId, boolean used) {
        return MfaBackupCode.builder()
                .userId(userId)
                .codeHash("hash_" + UUID.randomUUID())
                .used(used)
                .build();
    }

    private MfaBackupCode createAndSaveBackupCode(UUID userId, boolean used) {
        return mfaBackupCodeRepository.save(createBackupCode(userId, used));
    }
}
