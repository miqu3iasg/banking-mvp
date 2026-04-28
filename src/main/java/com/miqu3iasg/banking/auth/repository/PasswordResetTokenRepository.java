package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Query("SELECT prt FROM PasswordResetToken prt WHERE prt.tokenHash = :tokenHash AND prt.consumed = false AND prt.expiresAt > :now")
    Optional<PasswordResetToken> findValidToken(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE PasswordResetToken prt SET prt.consumed = true, prt.consumedAt = :now WHERE prt.userId = :userId AND prt.consumed = false")
    int consumeAllTokensForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.expiresAt < :before")
    int deleteExpiredTokens(@Param("before") Instant before);
}
