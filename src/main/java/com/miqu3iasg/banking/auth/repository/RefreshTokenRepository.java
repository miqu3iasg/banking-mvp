package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash AND rt.revoked = false AND rt.expiresAt > :now")
    Optional<RefreshToken> findValidToken(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.id = :id")
    Optional<RefreshToken> findByIdForUpdate(@Param("id") UUID id);

    List<RefreshToken> findByUserIdAndRevokedFalse(UUID userId);

    List<RefreshToken> findByFamilyId(UUID familyId);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.familyId = :familyId AND rt.id != :excludeId AND rt.revoked = false AND rt.expiresAt > :now")
    List<RefreshToken> findActiveTokensInFamily(@Param("familyId") UUID familyId, @Param("excludeId") UUID excludeId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now, rt.revokedReason = :reason WHERE rt.userId = :userId AND rt.revoked = false")
    int revokeAllUserTokens(@Param("userId") UUID userId, @Param("now") Instant now, @Param("reason") String reason);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now, rt.revokedReason = :reason WHERE rt.familyId = :familyId")
    int revokeFamilyTokens(@Param("familyId") UUID familyId, @Param("now") Instant now, @Param("reason") String reason);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :before")
    int deleteExpiredTokens(@Param("before") Instant before);

    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.userId = :userId AND rt.revoked = false AND rt.expiresAt > :now")
    long countActiveTokensByUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
