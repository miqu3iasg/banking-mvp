package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    @Query("SELECT ak FROM ApiKey ak WHERE ak.keyHash = :keyHash AND ak.revoked = false AND (ak.expiresAt IS NULL OR ak.expiresAt > :now)")
    Optional<ApiKey> findValidKey(@Param("keyHash") String keyHash, @Param("now") Instant now);

    List<ApiKey> findByOwnerId(UUID ownerId);

    List<ApiKey> findByKeyPrefix(String keyPrefix);

    List<ApiKey> findByOwnerIdAndRevokedFalse(UUID ownerId);

    @Query("SELECT ak FROM ApiKey ak WHERE ak.revoked = false AND ak.expiresAt IS NOT NULL AND ak.expiresAt <= :now")
    List<ApiKey> findExpiredKeys(@Param("now") Instant now);

    @Query("SELECT ak FROM ApiKey ak WHERE ak.revoked = false AND ak.rotationGracePeriodEnd IS NOT NULL AND ak.rotationGracePeriodEnd <= :now")
    List<ApiKey> findKeysPastGracePeriod(@Param("now") Instant now);

    @Modifying
    @Query("UPDATE ApiKey ak SET ak.revoked = true, ak.revokedAt = :now, ak.revokedReason = :reason WHERE ak.rotationGracePeriodEnd IS NOT NULL AND ak.rotationGracePeriodEnd <= :now")
    int revokeKeysPastGracePeriod(@Param("now") Instant now, @Param("reason") String reason);

    @Modifying
    @Query("UPDATE ApiKey ak SET ak.lastUsedAt = :now WHERE ak.id = :id")
    void updateLastUsed(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE ApiKey ak SET ak.revoked = true, ak.revokedAt = :now, ak.revokedReason = :reason WHERE ak.ownerId = :ownerId AND ak.revoked = false")
    int revokeAllByOwnerId(@Param("ownerId") UUID ownerId, @Param("now") Instant now, @Param("reason") String reason);
}
