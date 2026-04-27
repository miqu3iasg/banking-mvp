package com.miqu3iasg.banking.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.*;

import java.util.List;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

	private Jwt jwt = new Jwt();
	private Password password = new Password();
	private Lockout lockout = new Lockout();
	private RateLimit rateLimit = new RateLimit();
	private Session session = new Session();
	private Mfa mfa = new Mfa();
	private Redis redis = new Redis();
	private Email email = new Email();
	private Cors cors = new Cors();
	private Security security = new Security();

	public Jwt getJwt () {
		return jwt;
	}

	public Password getPassword () {
		return password;
	}

	public Lockout getLockout () {
		return lockout;
	}

	public RateLimit getRateLimit () {
		return rateLimit;
	}

	public Session getSession () {
		return session;
	}

	public Mfa getMfa () {
		return mfa;
	}

	public Redis getRedis () {
		return redis;
	}

	public Email getEmail () {
		return email;
	}

	public Cors getCors () {
		return cors;
	}

	public Security getSecurity () {
		return security;
	}

	@Getter
	@Setter
	public static class Jwt {
		@Min(300)
		@Max(86400)
		private int accessTokenExpirySeconds = 900;

		@Min(3600)
		@Max(604800)
		private int refreshTokenExpirySeconds = 604800;

		@Min(2048)
		private int keySize = 4096;

		private String publicKeyPath;
		private String privateKeyPath;
		private String issuer = "banking-mvp";
		private String audience = "banking-mvp-api";
	}

	@Getter
	@Setter
	public static class Password {
		@Min(8)
		@Max(128)
		private int minLength = 12;

		@Min(1)
		@Max(10)
		private int historyCount = 10;

		@Min(86400)
		@Max(7776000)
		private int expiryDays = 90;

		private boolean requireUppercase = true;
		private boolean requireLowercase = true;
		private boolean requireDigit = true;
		private boolean requireSpecial = true;

		@Min(1)
		@Max(3)
		private int minSpecialChars = 1;

		private boolean checkHaveIBeenPwned = false;

		private boolean hibpFailClosed = true;
	}

	@Getter
	@Setter
	public static class Lockout {
		@Min(3)
		@Max(10)
		private int maxFailedAttempts = 5;

		@Min(60)
		@Max(86400)
		private long initialLockoutSeconds = 300;

		@Min(60)
		@Max(604800)
		private long maxLockoutSeconds = 86400;

		private double lockoutMultiplier = 2.0;
	}

	@Getter
	@Setter
	public static class RateLimit {
		@Min(10)
		@Max(10000)
		private int requestsPerMinute = 100;

		@Min(10)
		@Max(1000)
		private int loginRequestsPerMinute = 10;

		@Min(5)
		@Max(100)
		private int passwordResetPerHour = 5;
	}

	@Getter
	@Setter
	public static class Session {
		@Min(1)
		@Max(10)
		private int maxConcurrentSessions = 5;

		@Min(300)
		@Max(86400)
		private int idleTimeoutSeconds = 1800;
	}

	@Getter
	@Setter
	public static class Mfa {
		@Min(30)
		@Max(300)
		private int totpWindowSeconds = 30;

		@Min(1)
		@Max(3)
		private int totpTolerance = 1;

		@Min(6)
		@Max(16)
		private int backupCodeCount = 8;

		@Min(8)
		@Max(16)
		private int backupCodeLength = 10;

		private String issuer = "BankingMVP";
	}

	@Getter
	@Setter
	public static class Redis {
		@Min(1)
		@Max(10)
		private int timeoutSeconds = 2;

		private boolean failOpen = false;
	}

	@Getter
	@Setter
	public static class Email {
		private boolean enabled = true;
		private int resendCooldownMinutes = 5;
		@Min(1)
		@Max(24)
		private int maxResendAttempts = 3;
	}

	@Getter
	@Setter
	public static class Cors {
		private List<String> allowedOrigins = List.of("http://localhost:3000", "http://localhost:8080");
	}

	@Getter
	@Setter
	public static class Security {
		private List<String> trustedProxyIps = List.of();
		private List<String> publicPaths = List.of(
			"/api/v1/auth/login",
			"/api/v1/auth/register",
			"/api/v1/auth/password/reset",
			"/api/v1/auth/password/reset/confirm",
			"/api/v1/public/**",
			"/v1/boleto/webhook/**",
			"/v1/pix/webhook/**",
			"/actuator/health",
			"/actuator/info",
			"/swagger-ui/**",
			"/v3/api-docs/**"
		);
	}
}
