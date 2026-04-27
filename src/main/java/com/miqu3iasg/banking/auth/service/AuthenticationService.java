package com.miqu3iasg.banking.auth.service;

import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.domain.RefreshToken;
import com.miqu3iasg.banking.auth.domain.RoleEntity;
import com.miqu3iasg.banking.auth.domain.User;
import com.miqu3iasg.banking.auth.exception.AuthFaultCode;
import com.miqu3iasg.banking.auth.exception.AuthenticationException;
import com.miqu3iasg.banking.auth.repository.UserRepository;
import com.miqu3iasg.banking.auth.security.JwtService;
import com.miqu3iasg.banking.auth.security.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

	private final UserRepository userRepository;
	private final PasswordService passwordService;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final LockoutService lockoutService;
	private final HashingService hashingService;
	private final AuditLogService auditLogService;

	@Transactional(noRollbackFor = AuthenticationException.class)
	public AuthenticationResult authenticate (String email, String password, String ipAddress, String userAgent, String deviceFingerprint) {
		String ipHash = hashingService.ipHash(ipAddress);
		String userAgentHash = hashingService.userAgentHash(userAgent);

		if (lockoutService.isRateLimited(ipHash)) {
			auditLogService.logLoginAttempt(null, null, ipHash, userAgentHash, "FAILURE", "RATE_LIMITED");
			throw new AuthenticationException(AuthFaultCode.RATE_002);
		}

		Optional<User> userOpt = userRepository.findByEmailWithRoles(email);

		if (userOpt.isEmpty()) {
			auditLogService.logLoginAttempt(null, hashingService.emailHash(email), ipHash, userAgentHash, "FAILURE", "USER_NOT_FOUND");
			throw new AuthenticationException(AuthFaultCode.AUTH_002);
		}

		User user = userOpt.get();

		if (lockoutService.isLocked(user)) {
			auditLogService.logLoginAttempt(user.getId(), user.getEmailHash(), ipHash, userAgentHash, "FAILURE", "ACCOUNT_LOCKED");
			throw new AuthenticationException(AuthFaultCode.AUTH_003); // ACCOUNT_LOCKED
		}

		if (user.getStatus() == AccountStatus.SUSPENDED) {
			auditLogService.logLoginAttempt(user.getId(), user.getEmailHash(), ipHash, userAgentHash, "FAILURE", "ACCOUNT_SUSPENDED");
			throw new AuthenticationException(AuthFaultCode.AUTH_004); // ACCOUNT_SUSPENDED
		}

		if (!passwordService.matches(password, user.getPasswordHash())) {
			lockoutService.recordFailedLogin(user, ipHash, userAgentHash, "INVALID_CREDENTIALS");
			auditLogService.logLoginAttempt(user.getId(), user.getEmailHash(), ipHash, userAgentHash, "FAILURE", "INVALID_CREDENTIALS");
			throw new AuthenticationException(AuthFaultCode.AUTH_002);
		}

		if (!user.isEmailVerified()) {
			auditLogService.logLoginAttempt(user.getId(), user.getEmailHash(), ipHash, userAgentHash, "FAILURE", "EMAIL_NOT_VERIFIED");
			throw new AuthenticationException(AuthFaultCode.AUTH_002);
		}

		lockoutService.recordSuccessfulLogin(user, ipHash, userAgentHash);

		Set<String> roles = new HashSet<>();
		for (RoleEntity role : user.getRoles()) {
			roles.add(role.getName().name());
		}

		String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles, null, user.getStatus());

		RefreshTokenService.RefreshTokenCreationResult refreshTokenResult = refreshTokenService.createRefreshToken(
			user.getId(), ipHash, userAgentHash, deviceFingerprint
		);

		userRepository.updateLastLoginAt(user.getId(), Instant.now());

		auditLogService.logLoginAttempt(user.getId(), user.getEmailHash(), ipHash, userAgentHash, "SUCCESS", null);

		boolean requiresMfa = requiresMfa(user);

		return new AuthenticationResult(
			accessToken,
			refreshTokenResult.entity().getId().toString(),
			roles,
			requiresMfa
		);
	}

	@Transactional
	public AuthenticationResult refreshTokens (String rawRefreshToken, String ipAddress, String userAgent) {
		String ipHash = hashingService.ipHash(ipAddress);
		String userAgentHash = hashingService.userAgentHash(userAgent);

		RefreshToken currentToken = refreshTokenService.validateRefreshToken(rawRefreshToken);

		RefreshToken newToken = refreshTokenService.rotateRefreshToken(
			currentToken, ipHash, userAgentHash
		);

		Optional<User> userOpt = userRepository.findByIdWithRoles(newToken.getUserId());
		if (userOpt.isEmpty()) {
			throw new AuthenticationException(AuthFaultCode.AUTH_002);
		}

		User user = userOpt.get();

		if (user.getStatus() == AccountStatus.SUSPENDED || user.getStatus() == AccountStatus.DELETED) {
			throw new AuthenticationException(AuthFaultCode.AUTH_002);
		}

		if (user.isLocked()) {
			throw new AuthenticationException(AuthFaultCode.AUTH_003);
		}

		if (!user.isEmailVerified()) {
			throw new AuthenticationException(AuthFaultCode.AUTH_002);
		}

		Set<String> roles = new HashSet<>();
		for (RoleEntity role : user.getRoles()) {
			roles.add(role.getName().name());
		}

		String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles, null, user.getStatus());

		auditLogService.logTokenRefresh(user.getId(), "SUCCESS", null);

		boolean requiresMfa = requiresMfa(user);

		return new AuthenticationResult(
			accessToken,
			newToken.getId().toString(),
			roles,
			requiresMfa
		);
	}

	@Transactional
	public void logout (String accessToken, String refreshTokenValue) {
		if (accessToken != null) {
			jwtService.blacklistToken(accessToken, jwtService.getRemainingTtlSeconds(accessToken));
		}

		if (refreshTokenValue != null) {
			try {
				RefreshToken token = refreshTokenService.validateRefreshToken(refreshTokenValue);
				refreshTokenService.revokeToken(token, "USER_LOGOUT");
			} catch (Exception e) {
				log.debug("Could not revoke refresh token: {}", e.getMessage());
			}
		}
	}

	@Transactional
	public void logoutAllDevices (UUID userId) {
		refreshTokenService.revokeAllUserTokens(userId, "GLOBAL_LOGOUT");
	}

	@Transactional
	public void revokeAllTokensForUser (UUID userId, String reason) {
		refreshTokenService.revokeAllUserTokens(userId, reason);
	}

	private boolean requiresMfa (User user) {
		if (!user.isMfaEnabled()) {
			return false;
		}

		for (RoleEntity role : user.getRoles()) {
			if (role.isMfaRequired()) {
				return true;
			}
		}

		return false;
	}

	public record AuthenticationResult(
		String accessToken,
		String refreshTokenId,
		Set<String> roles,
		boolean requiresMfa
	) { }
}
