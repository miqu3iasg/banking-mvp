package com.miqu3iasg.banking.auth.service;

import com.miqu3iasg.banking.auth.config.AuthProperties;
import com.miqu3iasg.banking.auth.domain.MfaBackupCode;
import com.miqu3iasg.banking.auth.domain.User;
import com.miqu3iasg.banking.auth.exception.AuthFaultCode;
import com.miqu3iasg.banking.auth.exception.MfaException;
import com.miqu3iasg.banking.auth.repository.MfaBackupCodeRepository;
import com.miqu3iasg.banking.auth.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base32;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Provides TOTP-based multi-factor authentication (RFC 6238) and backup code management.
 *
 * <p>Security properties:
 * <ul>
 *   <li>Secrets are stored encrypted via {@link EncryptionService} (AES-256-GCM).</li>
 *   <li>TOTP secrets are Base32-encoded, compatible with all major authenticator apps.</li>
 *   <li>Replay protection: only the specific counter slot that matched is marked used.</li>
 *   <li>Constant-time comparison prevents timing oracle attacks.</li>
 *   <li>Backup codes are hashed with SHA-256 before storage.</li>
 * </ul>
 */
@Slf4j
@Service
public class MfaService {

	// TOTP constants (RFC 6238)
	private static final String HMAC_ALGORITHM = "HmacSHA1";
	private static final String KEY_SPEC_ALGORITHM = "SHA1";
	private static final int TOTP_DIGITS = 6;
	private static final int TOTP_PERIOD = 30; // seconds
	private static final int SECRET_BYTES = 20; // 160-bit secret per RFC recommendation

	// Redis key pattern: mfa:used:{userId}:{counter}
	private static final String REPLAY_KEY_PATTERN = "mfa:used:%s:%d";

	private final UserRepository userRepository;
	private final MfaBackupCodeRepository backupCodeRepository;
	private final AuthProperties authProperties;
	private final StringRedisTemplate redisTemplate;
	private final EncryptionService encryptionService;
	private final HashingService hashingService;
	private final Counter mfaValidationSuccessCounter;
	private final Counter mfaValidationFailureCounter;
	private final SecureRandom secureRandom = new SecureRandom();
	private final Base32 base32 = new Base32();

	@Autowired
	public MfaService (
		UserRepository userRepository,
		MfaBackupCodeRepository backupCodeRepository,
		AuthProperties authProperties,
		StringRedisTemplate redisTemplate,
		EncryptionService encryptionService,
		HashingService hashingService,
		MeterRegistry meterRegistry) {

		this.userRepository = userRepository;
		this.backupCodeRepository = backupCodeRepository;
		this.authProperties = authProperties;
		this.redisTemplate = redisTemplate;
		this.encryptionService = encryptionService;
		this.hashingService = hashingService;

		// Counters registered eagerly so Prometheus sees them from startup.
		// TODO: move to metrics class
		this.mfaValidationSuccessCounter = Counter.builder("auth.mfa.validation")
			.description("MFA validation outcomes")
			.tag("outcome", "success")
			.register(meterRegistry);

		this.mfaValidationFailureCounter = Counter.builder("auth.mfa.validation")
			.description("MFA validation outcomes")
			.tag("outcome", "failure")
			.register(meterRegistry);
	}


	/**
	 * Generates a new TOTP secret for the user, encrypts it, and persists it.
	 *
	 * <p>The secret is returned as a Base32 string so it can be embedded directly
	 * into the {@code otpauth://} URI and scanned by any authenticator app without
	 * additional encoding steps.
	 *
	 * @param user the user for whom the secret is generated
	 * @return Base32-encoded TOTP secret (not yet activated; call {@link #enableMfa} to confirm)
	 */
	@Transactional
	public String generateMfaSecret (User user) {
		byte[] secretBytes = new byte[SECRET_BYTES];
		secureRandom.nextBytes(secretBytes);

		// RFC 6238 and all major authenticator apps (Google Authenticator, Authy, 1Password)
		// expect the `secret` parameter of an otpauth:// URI to be Base32-encoded.
		String base32Secret = base32.encodeToString(secretBytes).replaceAll("=", "");

		// Store encrypted so a DB dump does not expose TOTP seeds.
		user.setMfaSecret(encryptionService.encrypt(base32Secret));
		userRepository.save(user);

		log.debug("MFA secret generated for user: {}", user.getId());
		return base32Secret;
	}

	/**
	 * Builds an {@code otpauth://totp/…} URI for QR-code generation.
	 *
	 * @param base32Secret the Base32-encoded secret returned by {@link #generateMfaSecret}
	 * @param email        the user's email address (used as the account label)
	 * @return fully-qualified OTP Auth URI
	 */
	public String generateOtpAuthUri (String base32Secret, String email) {
		String issuer = authProperties.getMfa().getIssuer();
		String encodedAccount = URLEncoder.encode(email, StandardCharsets.UTF_8);

		return String.format(
			"otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
			encodedAccount,
			base32Secret,
			URLEncoder.encode(issuer, StandardCharsets.UTF_8),
			TOTP_DIGITS,
			TOTP_PERIOD);
	}

	/**
	 * Regenerates all backup codes for the user. Existing unused codes are deleted first.
	 *
	 * @param user the user
	 * @return list of plaintext backup codes (show once; not recoverable after this call)
	 */
	@Transactional
	public List<String> generateBackupCodes (User user) {
		backupCodeRepository.deleteByUserId(user.getId());

		int codeCount = authProperties.getMfa().getBackupCodeCount();
		int codeLength = authProperties.getMfa().getBackupCodeLength();

		List<String> rawCodes = generateRawCodes(codeCount, codeLength);

		for (String code : rawCodes) {
			String codeHash = hashingService.sha256(code);
			MfaBackupCode backupCode = MfaBackupCode.builder()
				.userId(user.getId())
				.codeHash(codeHash)
				.build();
			backupCodeRepository.save(backupCode);
		}

		log.info("Backup codes regenerated for user: {}", user.getId());
		return rawCodes;
	}

	/**
	 * Validates a TOTP code against the user's current secret.
	 *
	 * <p>Replay protection: when a valid code is found at counter offset {@code i},
	 * only that specific counter slot is written to Redis — previous and future slots
	 * in the tolerance window remain available for legitimate use.
	 *
	 * @param user the user
	 * @param code the 6-digit TOTP code submitted by the user
	 * @return {@code true} if the code is valid and has not been used before
	 */
	public boolean validateTotp (User user, String code) {
		if (user.getMfaSecret() == null) {
			throw new MfaException(AuthFaultCode.MFA_004);
		}

		String decryptedSecret = encryptionService.decrypt(user.getMfaSecret());
		int tolerance = authProperties.getMfa().getTotpTolerance();
		long currentCounter = System.currentTimeMillis() / 1_000L / TOTP_PERIOD;

		for (int i = -tolerance; i <= tolerance; i++) {
			long counter = currentCounter + i;
			String replayKey = String.format(REPLAY_KEY_PATTERN, user.getId(), counter);

			// Skip already-used slots (prevent replay within the tolerance window).
			if (Boolean.TRUE.equals(redisTemplate.hasKey(replayKey))) {
				continue;
			}

			String expectedCode = generateTotpForCounter(decryptedSecret, counter);
			if (constantTimeEquals(expectedCode, code)) {
				// FIX (Medium): mark ONLY the matched counter slot, not the entire window.
				// Marking all slots prevented users from completing MFA if their clock drifted
				// slightly and they needed a tolerance-window code on their next attempt.
				long ttlSeconds = (long) TOTP_PERIOD * (tolerance * 2 + 2);
				redisTemplate.opsForValue().set(
					replayKey, "1", Duration.ofSeconds(ttlSeconds));

				mfaValidationSuccessCounter.increment();
				return true;
			}
		}

		mfaValidationFailureCounter.increment();
		return false;
	}

	/**
	 * Validates and consumes a backup code.
	 *
	 * @param user the user
	 * @param code the plaintext backup code submitted by the user
	 * @return {@code true} if the code was valid and has been marked used
	 */
	@Transactional
	public boolean validateBackupCode (User user, String code) {
		String codeHash = hashingService.sha256(code);

		return backupCodeRepository.findByCodeHashAndUsedFalse(codeHash)
			.filter(bc -> bc.getUserId().equals(user.getId()))
			.map(bc -> {
				bc.markUsed();
				backupCodeRepository.save(bc);
				log.info("Backup code consumed for user: {}", user.getId());
				return true;
			})
			.orElse(false);
	}

	/**
	 * Activates MFA for the user after verifying the provided TOTP code.
	 * Also regenerates backup codes.
	 *
	 * @param user             the user
	 * @param verificationCode the TOTP code from the user's authenticator app
	 * @throws MfaException if the verification code is invalid
	 */
	@Transactional
	public void enableMfa (User user, String verificationCode) {
		if (!validateTotp(user, verificationCode)) {
			throw new MfaException(AuthFaultCode.MFA_002);
		}
		user.setMfaEnabled(true);
		userRepository.save(user);
		generateBackupCodes(user);
		log.info("MFA enabled for user: {}", user.getId());
	}

	/**
	 * Disables MFA for the user and removes all associated secrets and backup codes.
	 *
	 * @param user the user
	 */
	@Transactional
	public void disableMfa (User user) {
		user.setMfaEnabled(false);
		user.setMfaSecret(null);
		userRepository.save(user);
		backupCodeRepository.deleteByUserId(user.getId());
		log.info("MFA disabled for user: {}", user.getId());
	}

	/**
	 * Returns the number of unused backup codes remaining for the user.
	 */
	public int getRemainingBackupCodes (UUID userId) {
		return backupCodeRepository.countByUserIdAndUsedFalse(userId);
	}

	/**
	 * Computes a TOTP code for the given Base32-encoded secret and counter value.
	 *
	 * <p>Implements RFC 6238 / HOTP (RFC 4226):
	 * <ol>
	 *   <li>Decode secret from Base32.</li>
	 *   <li>Compute HMAC-SHA1 of the 8-byte big-endian counter.</li>
	 *   <li>Dynamic truncation → 31-bit integer → mod 10^digits.</li>
	 * </ol>
	 *
	 * @param base32Secret Base32-encoded TOTP secret
	 * @param counter      TOTP time counter (Unix time / period)
	 * @return zero-padded {@code TOTP_DIGITS}-digit OTP string
	 */
	private String generateTotpForCounter (String base32Secret, long counter) {
		try {
			// FIX (Critical): decode from Base32 (was incorrectly using Base64).
			byte[] key = base32.decode(base32Secret);

			// Encode counter as big-endian 8-byte array.
			byte[] counterBytes = new byte[8];
			long tmp = counter;
			for (int i = 7; i >= 0; i--) {
				counterBytes[i] = (byte) (tmp & 0xFF);
				tmp >>= 8;
			}

			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(key, KEY_SPEC_ALGORITHM));
			byte[] hash = mac.doFinal(counterBytes);

			// Dynamic truncation (RFC 4226 §5.3).
			int offset = hash[hash.length - 1] & 0x0F;
			int binary = ((hash[offset] & 0x7F) << 24)
				| ((hash[offset + 1] & 0xFF) << 16)
				| ((hash[offset + 2] & 0xFF) << 8)
				| (hash[offset + 3] & 0xFF);

			int otp = binary % (int) Math.pow(10, TOTP_DIGITS);
			return String.format("%0" + TOTP_DIGITS + "d", otp);

		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException("Failed to generate TOTP code", e);
		}
	}

	/**
	 * Generates a single backup code of the specified length using a safe alphabet
	 * that excludes visually ambiguous characters (0/O, 1/I/l).
	 */
	private String generateBackupCode (int length) {
		final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
		StringBuilder sb = new StringBuilder(length + 1);
		for (int i = 0; i < length; i++) {
			if (i > 0 && i == length / 2) {
				sb.append('-'); // split long codes for readability, e.g. "ABCD-EFGH"
			}
			sb.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
		}
		return sb.toString();
	}

	private List<String> generateRawCodes (int count, int length) {
		return java.util.stream.IntStream.range(0, count)
			.mapToObj(i -> generateBackupCode(length))
			.toList();
	}

	/**
	 * Constant-time string comparison to prevent timing oracle attacks.
	 * Returns {@code false} if either argument is null.
	 */
	private boolean constantTimeEquals (String a, String b) {
		if (a == null || b == null) return false;
		return constantTimeEquals(
			a.getBytes(StandardCharsets.UTF_8),
			b.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Constant-time byte-array comparison.
	 * Always iterates the full length of {@code a} regardless of mismatches.
	 */
	private boolean constantTimeEquals (byte[] a, byte[] b) {
		if (a.length != b.length) return false;
		int result = 0;
		for (int i = 0; i < a.length; i++) {
			result |= (a[i] ^ b[i]);
		}
		return result == 0;
	}
}
