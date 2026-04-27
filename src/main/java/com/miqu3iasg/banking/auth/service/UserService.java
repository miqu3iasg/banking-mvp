package com.miqu3iasg.banking.auth.service;

import com.miqu3iasg.banking.auth.domain.*;
import com.miqu3iasg.banking.auth.exception.AuthFaultCode;
import com.miqu3iasg.banking.auth.exception.PasswordException;
import com.miqu3iasg.banking.auth.exception.RegistrationException;
import com.miqu3iasg.banking.auth.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordHistoryRepository passwordHistoryRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final EmailVerificationTokenRepository emailVerificationTokenRepository;
	private final PasswordService passwordService;
	private final HashingService hashingService;
	private final com.miqu3iasg.banking.auth.security.RefreshTokenService refreshTokenService;
	private final com.miqu3iasg.banking.auth.repository.ApiKeyRepository apiKeyRepository;

	@Transactional
	public User createUser (String email, String password, boolean consentEmail) {
	if (userRepository.existsByEmail(email)) {
		throw new RegistrationException(AuthFaultCode.REG_001);
	}

		validateEmail(email);

		passwordService.validatePassword(password);

		User user = new User();
		user.setEmail(email);
		user.setEmailHash(hashingService.emailHash(email));
		user.setPasswordHash(passwordService.encode(password));
		user.setStatus(AccountStatus.PENDING_VERIFICATION);
		user.setEmailVerified(false);
		user.setMfaEnabled(false);
		user.setFailedLoginAttempts(0);
		user.setConsentEmail(consentEmail);
		if (consentEmail) {
			user.setConsentTimestamp(Instant.now());
		}

		Set<RoleEntity> defaultRoles = new HashSet<>();
		roleRepository.findByName(Role.ROLE_USER).ifPresent(defaultRoles::add);
		user.setRoles(defaultRoles);

		user = userRepository.save(user);

		String newHash = user.getPasswordHash();
		savePasswordHistory(user.getId(), newHash);

		return user;
	}

	@Transactional
	public void requestPasswordReset (String email, String ipHash) {
		Optional<User> userOpt = userRepository.findByEmailWithRoles(email);

		if (userOpt.isEmpty()) {
			passwordService.encode("dummy-password-for-timing");
			return;
		}

		User user = userOpt.get();

		passwordResetTokenRepository.consumeAllTokensForUser(user.getId(), Instant.now());

		String rawToken = hashingService.generateSecureToken();
		String tokenHash = hashingService.hashToken(rawToken);
		Instant expiresAt = Instant.now().plusSeconds(900);

		PasswordResetToken resetToken = PasswordResetToken.builder()
			.tokenHash(tokenHash)
			.userId(user.getId())
			.expiresAt(expiresAt)
			.createdFromIpHash(ipHash)
			.build();

		passwordResetTokenRepository.save(resetToken);

		log.info("Password reset requested for user: {}", user.getId());
	}

	@Transactional
	public void confirmPasswordReset (String rawToken, String newPassword, String ipHash) {
		String tokenHash = hashingService.hashToken(rawToken);

		PasswordResetToken resetToken = passwordResetTokenRepository.findValidToken(tokenHash, Instant.now())
			.orElseThrow(() -> new PasswordException(AuthFaultCode.PWD_005));

		resetToken.incrementAttempt();
		if (resetToken.getAttemptCount() >= 5) {
			resetToken.consume();
			passwordResetTokenRepository.save(resetToken);
			throw new PasswordException(AuthFaultCode.PWD_005);
		}

		passwordService.validatePassword(newPassword);

		List<PasswordHistory> recentPasswords = passwordHistoryRepository.findRecentByUserId(resetToken.getUserId(), 10);
		Set<String> history = new HashSet<>();
		for (PasswordHistory ph : recentPasswords) {
			history.add(ph.getPasswordHash());
		}

		if (passwordService.isPasswordInHistory(newPassword, history)) {
			throw new PasswordException(AuthFaultCode.PWD_003);
		}

		User user = userRepository.findById(resetToken.getUserId())
			.orElseThrow(() -> new PasswordException(AuthFaultCode.PWD_006));

		String newHash = passwordService.encode(newPassword);
		user.setPasswordHash(newHash);
		user.setPasswordChangedAt(Instant.now());
		user.setForcePasswordReset(false);
		userRepository.save(user);

		savePasswordHistory(user.getId(), newHash);

		resetToken.consume();
		passwordResetTokenRepository.save(resetToken);
	}

	@Transactional
	public void changePassword (UUID userId, String currentPassword, String newPassword) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new PasswordException(AuthFaultCode.PWD_006));

		if (!passwordService.matches(currentPassword, user.getPasswordHash())) {
			throw new PasswordException(AuthFaultCode.PWD_006);
		}

		passwordService.validatePassword(newPassword);

		List<PasswordHistory> recentPasswords = passwordHistoryRepository.findRecentByUserId(userId, 10);
		Set<String> history = new HashSet<>();
		for (PasswordHistory ph : recentPasswords) {
			history.add(ph.getPasswordHash());
		}

		if (passwordService.isPasswordInHistory(newPassword, history)) {
			throw new PasswordException(AuthFaultCode.PWD_003);
		}

		String newHash = passwordService.encode(newPassword);
		user.setPasswordHash(newHash);
		user.setPasswordChangedAt(Instant.now());
		user.setForcePasswordReset(false);
		userRepository.save(user);

		savePasswordHistory(userId, newHash);
	}

	@Transactional
	public void verifyEmail (UUID userId, String token) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new RegistrationException(AuthFaultCode.REG_005));

		String tokenHash = hashingService.hashToken(token);
		EmailVerificationToken verificationToken = emailVerificationTokenRepository.findValidToken(tokenHash, Instant.now())
			.orElseThrow(() -> new RegistrationException(AuthFaultCode.REG_005));

		if (verificationToken.isExpired()) {
			throw new RegistrationException(AuthFaultCode.REG_004);
		}

		user.setEmailVerified(true);
		user.setStatus(AccountStatus.ACTIVE);
		userRepository.save(user);

		verificationToken.consume();
		emailVerificationTokenRepository.save(verificationToken);

		log.info("Email verified for user: {}", user.getId());
	}

	@Transactional(readOnly = true)
	public Optional<User> findByEmail (String email) {
		return userRepository.findByEmailWithRoles(email);
	}

	@Transactional(readOnly = true)
	public Optional<User> findById (UUID userId) {
		return userRepository.findByIdWithRoles(userId);
	}

	@Transactional
	public void updateLastLogin (UUID userId) {
		userRepository.findById(userId).ifPresent(user -> {
			user.setLastLoginAt(Instant.now());
			userRepository.save(user);
		});
	}

	@Transactional
	public void updatePassword (UUID userId, String newPassword, boolean forceReset) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new PasswordException(AuthFaultCode.PWD_006));

		if (!forceReset) {
			List<PasswordHistory> recentPasswords = passwordHistoryRepository.findRecentByUserId(userId, 10);
			Set<String> history = new HashSet<>();
			for (PasswordHistory ph : recentPasswords) {
				history.add(ph.getPasswordHash());
			}

			if (passwordService.isPasswordInHistory(newPassword, history)) {
				throw new PasswordException(AuthFaultCode.PWD_003);
			}
		}

		passwordService.validatePassword(newPassword);

		String newHash = passwordService.encode(newPassword);
		user.setPasswordHash(newHash);
		user.setPasswordChangedAt(Instant.now());
		user.setForcePasswordReset(forceReset);

		if (!forceReset) {
			savePasswordHistory(userId, newHash);
		}

		userRepository.save(user);
	}

	@Transactional
	public void suspendUser (UUID userId, String reason) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("User not found"));

		user.setStatus(AccountStatus.SUSPENDED);
		user.setSuspensionReason(reason);
		user.setSuspensionTimestamp(Instant.now());
		userRepository.save(user);
	}

	@Transactional
	public void unsuspendUser (UUID userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("User not found"));

		user.setStatus(AccountStatus.ACTIVE);
		user.setSuspensionReason(null);
		user.setSuspensionTimestamp(null);
		userRepository.save(user);
	}

	@Transactional
	public void softDeleteUser (UUID userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("User not found"));

		refreshTokenService.revokeAllUserTokens(userId, "USER_DELETED");

		apiKeyRepository.revokeAllByOwnerId(userId, Instant.now(), "USER_DELETED");

		user.setStatus(AccountStatus.DELETED);
		String tombstoneEmail = "deleted." + hashingService.sha256(user.getEmail()) + "@deleted.invalid";
		user.setEmail(tombstoneEmail);
		user.setPasswordHash(null);
		userRepository.save(user);
	}

	private void savePasswordHistory (UUID userId, String passwordHash) {
		PasswordHistory history = PasswordHistory.builder()
			.userId(userId)
			.passwordHash(passwordHash)
			.build();
		passwordHistoryRepository.save(history);
	}

	private void validateEmail (String email) {
		if (email == null || email.isBlank()) {
			throw new RegistrationException("Email is required", AuthFaultCode.REG_002);
		}

		String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
		if (!email.matches(emailRegex)) {
			throw new RegistrationException("Invalid email format", AuthFaultCode.REG_002);
		}

		if (email.length() > 320) {
			throw new RegistrationException("Email too long", AuthFaultCode.REG_002);
		}
	}
}
