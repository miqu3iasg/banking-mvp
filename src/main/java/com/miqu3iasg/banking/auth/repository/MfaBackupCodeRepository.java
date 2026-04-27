package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.MfaBackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MfaBackupCodeRepository extends JpaRepository<MfaBackupCode, UUID> {

    List<MfaBackupCode> findByUserIdAndUsedFalse(UUID userId);

    Optional<MfaBackupCode> findByCodeHash(String codeHash);

    Optional<MfaBackupCode> findByCodeHashAndUsedFalse(String codeHash);

    int countByUserIdAndUsedFalse(UUID userId);

    int countByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
