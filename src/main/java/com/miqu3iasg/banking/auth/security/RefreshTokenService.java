package com.miqu3iasg.banking.auth.security;

import com.miqu3iasg.banking.auth.config.AuthProperties;
import com.miqu3iasg.banking.auth.domain.RefreshToken;
import com.miqu3iasg.banking.auth.exception.AuthFaultCode;
import com.miqu3iasg.banking.auth.exception.TokenException;
import com.miqu3iasg.banking.auth.repository.RefreshTokenRepository;
import com.miqu3iasg.banking.auth.service.HashingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

	private static final String REVOKE_REASON_ROTATION = "ROTATION";
	private static final String REVOKE_REASON_FAMILY_REUSE = "FAMILY_REUSE";
	private static final String REVOKE_REASON_FAMILY_ATTACK = "FAMILY_REUSE_DETECTED";

	private final RefreshTokenRepository refreshTokenRepository;
	private final AuthProperties authProperties;
	private final HashingService hashingService;

	@Transactional
	public RefreshTokenCreationResult createRefreshToken (
		UUID userId, String ipHash, String userAgentHash, String deviceFingerprint) {

		String rawToken = hashingService.generateSecureToken();

		String tokenHash = hashingService.hashToken(rawToken);
		UUID familyId = UUID.randomUUID();

		Instant expiresAt = Instant.now()
			.plusSeconds(authProperties.getJwt().getRefreshTokenExpirySeconds());

		RefreshToken entity = RefreshToken.builder()
			.tokenHash(tokenHash)           // persisted field is the HASH
			.userId(userId)
			.familyId(familyId)
			.ipHash(ipHash)
			.userAgentHash(userAgentHash)
			.deviceFingerprint(deviceFingerprint)
			.expiresAt(expiresAt)
			.createdFromIpHash(ipHash)
			.build();

		entity = refreshTokenRepository.save(entity);

		log.debug("Created refresh token family {} for user {}", familyId, userId);

		return new RefreshTokenCreationResult(entity, rawToken);
	}

	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public RefreshToken rotateRefreshToken (
		RefreshToken currentToken, String ipHash, String userAgentHash) {

		// Acquire a pessimistic lock on the row to prevent concurrent rotation.
		RefreshToken locked = refreshTokenRepository.findByIdForUpdate(currentToken.getId())
			.orElseThrow(() -> new TokenException(AuthFaultCode.AUTH_007));

		if (!locked.isValid()) {
			throw new TokenException(AuthFaultCode.AUTH_007);
		}

		// Detect token theft: if another active token exists in the same family,
		// an attacker is replaying a previously-issued (and already-rotated) token.
		List<RefreshToken> otherFamilyTokens = refreshTokenRepository.findActiveTokensInFamily(
			locked.getFamilyId(), locked.getId(), Instant.now());

		if (!otherFamilyTokens.isEmpty()) {
			log.warn("Token theft detected — revoking family {} for user {}",
				locked.getFamilyId(), locked.getUserId());

			refreshTokenRepository.revokeFamilyTokens(
				locked.getFamilyId(), Instant.now(), REVOKE_REASON_FAMILY_ATTACK);

			locked.setRevoked(true);
			locked.setRevokedAt(Instant.now());
			locked.setRevokedReason(REVOKE_REASON_FAMILY_REUSE);
			refreshTokenRepository.save(locked);

			throw new TokenException(AuthFaultCode.AUTH_010);
		}

		// Revoke the consumed token.
		locked.setRevoked(true);
		locked.setRevokedAt(Instant.now());
		locked.setRevokedReason(REVOKE_REASON_ROTATION);
		refreshTokenRepository.save(locked);

		// Issue the new token in the same family.
		RefreshTokenCreationResult newResult = createRefreshTokenInFamily(
			locked.getUserId(),
			locked.getFamilyId(),
			locked.getCreatedFromIpHash(),
			ipHash,
			userAgentHash,
			locked.getDeviceFingerprint());

		// Link old → new for audit chain.
		locked.setReplacedByTokenId(newResult.entity().getId());
		refreshTokenRepository.save(locked);

		log.debug("Rotated refresh token in family {} for user {}",
			locked.getFamilyId(),
			locked.getUserId());

		return newResult.entity();
	}

	@Transactional
	public RefreshToken validateRefreshToken (String rawToken) {
		String tokenHash = hashingService.hashToken(rawToken);

		RefreshToken token = refreshTokenRepository.findValidToken(tokenHash, Instant.now())
			.orElseThrow(() -> new TokenException(AuthFaultCode.AUTH_007));

		try {
			token.setLastUsedAt(Instant.now());
			refreshTokenRepository.save(token);
		} catch (Exception e) {
			log.warn("Could not update lastUsedAt for refresh token {}: {}", token.getId(), e.getMessage());
		}

		return token;
	}

	@Transactional
	public void revokeToken (RefreshToken token, String reason) {
		token.setRevoked(true);
		token.setRevokedAt(Instant.now());
		token.setRevokedReason(reason);
		refreshTokenRepository.save(token);
		log.debug("Revoked refresh token {} (reason: {})", token.getId(), reason);
	}

	@Transactional
	public void revokeAllUserTokens (UUID userId, String reason) {
		int count = refreshTokenRepository.revokeAllUserTokens(userId, Instant.now(), reason);
		log.info("Revoked {} refresh token(s) for user {} (reason: {})", count, userId, reason);
	}

	@Transactional
	public void revokeTokenFamily (UUID familyId, String reason) {
		int count = refreshTokenRepository.revokeFamilyTokens(familyId, Instant.now(), reason);
		log.debug("Revoked {} token(s) in family {} (reason: {})", count, familyId, reason);
	}

	public boolean hasExceededSessionLimit (UUID userId) {
		long active = refreshTokenRepository.countActiveTokensByUser(userId, Instant.now());
		return active >= authProperties.getSession().getMaxConcurrentSessions();
	}

	public int getActiveTokenCount (UUID userId) {
		return (int) refreshTokenRepository.countActiveTokensByUser(userId, Instant.now());
	}

	private RefreshTokenCreationResult createRefreshTokenInFamily (
		UUID userId, UUID familyId, String originalIpHash,
		String currentIpHash, String userAgentHash, String deviceFingerprint) {

		String rawToken = hashingService.generateSecureToken();
		String tokenHash = hashingService.hashToken(rawToken);
		Instant expiresAt = Instant.now().plusSeconds(
			authProperties.getJwt().getRefreshTokenExpirySeconds());

		RefreshToken entity = RefreshToken.builder()
			.tokenHash(tokenHash)
			.userId(userId)
			.familyId(familyId)
			.ipHash(currentIpHash)
			.userAgentHash(userAgentHash)
			.deviceFingerprint(deviceFingerprint)
			.expiresAt(expiresAt)
			.createdFromIpHash(originalIpHash)
			.build();

		entity = refreshTokenRepository.save(entity);
		return new RefreshTokenCreationResult(entity, rawToken);
	}

	public record RefreshTokenCreationResult(RefreshToken entity, String rawToken) { }
}
