package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Query("SELECT evt FROM EmailVerificationToken evt WHERE evt.tokenHash = :tokenHash AND evt.consumed = false AND evt.expiresAt > :now")
    Optional<EmailVerificationToken> findValidToken(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE EmailVerificationToken evt SET evt.consumed = true, evt.consumedAt = :now WHERE evt.userId = :userId AND evt.consumed = false")
    int consumeAllTokensForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM EmailVerificationToken evt WHERE evt.expiresAt < :before")
    int deleteExpiredTokens(@Param("before") Instant before);
}
