package com.miqu3iasg.banking.auth.security;

import com.miqu3iasg.banking.auth.config.AuthProperties;
import com.miqu3iasg.banking.auth.exception.AuthFaultCode;
import com.miqu3iasg.banking.auth.exception.TokenException;
import io.jsonwebtoken.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.*;
import com.miqu3iasg.banking.auth.domain.AccountStatus;

@Service
public class JwtService {

	private final JwtKeyProvider keyProvider;
	private final AuthProperties authProperties;
	private final com.miqu3iasg.banking.auth.config.RedisConfig.TokenBlacklistService tokenBlacklistService;

	private volatile JwtParser currentParser;
	private volatile JwtParser previousParser;
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtService.class);

	public JwtService(JwtKeyProvider keyProvider, AuthProperties authProperties, 
	                  com.miqu3iasg.banking.auth.config.RedisConfig.TokenBlacklistService tokenBlacklistService) {
		this.keyProvider = keyProvider;
		this.authProperties = authProperties;
		this.tokenBlacklistService = tokenBlacklistService;
	}

	@PostConstruct
	public void init () {
		currentParser = buildParser(keyProvider.getPublicKey());
		rebuildPreviousParser();
	}

	private JwtParser buildParser(PublicKey publicKey) {
		return Jwts.parser()
			.verifyWith(publicKey)
			.requireAudience(authProperties.getJwt().getAudience())
			.requireIssuer(authProperties.getJwt().getIssuer())
			.build();
	}

	private void rebuildPreviousParser() {
		KeyPair previousKey = keyProvider.getPreviousKeyPair();
		if (previousKey != null) {
			previousParser = buildParser(previousKey.getPublic());
		} else {
			previousParser = null;
		}
	}

	public String generateAccessToken (UUID userId, String email, Set<String> roles, Map<String, Object> additionalClaims, AccountStatus status) {
		Instant now = Instant.now();
		Instant expiry = now.plusSeconds(authProperties.getJwt().getAccessTokenExpirySeconds());

		String keyId = keyProvider.getKeyId();
		String jti = UUID.randomUUID().toString();

		JwtBuilder builder = Jwts.builder()
			.id(jti)
			.subject(userId.toString())
			.claim("email", email)
			.claim("roles", new ArrayList<>(roles))
			.claim("status", status != null ? status.name() : "ACTIVE")
			.issuer(authProperties.getJwt().getIssuer())
			.audience().add(authProperties.getJwt().getAudience()).and()
			.issuedAt(Date.from(now))
			.expiration(Date.from(expiry));

		builder.header().add("kid", keyId).and();

		if (additionalClaims != null) {
			additionalClaims.forEach(builder::claim);
		}

		return builder.signWith(keyProvider.getPrivateKey())
			.compact();
	}

	public Claims parseToken (String token) {
		Claims claims = tryParse(currentParser, token);
		if (claims == null && previousParser != null) {
			claims = tryParse(previousParser, token);
		}
		if (claims == null) {
			throw new TokenException(AuthFaultCode.AUTH_007);
		}
		return claims;
	}

	private Claims tryParse(JwtParser parser, String token) {
		try {
			return parser.parseSignedClaims(token).getPayload();
		} catch (ExpiredJwtException e) {
			log.debug("JWT token expired: {}", e.getMessage());
			throw new TokenException(AuthFaultCode.AUTH_008, e);
	} catch (SecurityException | MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
		log.warn("Invalid JWT token: {}", e.getMessage());
		return null;
	}
	}

	public boolean validateToken (String token) {
		try {
			Claims claims = parseToken(token);
			String jti = claims.getId();

			if (tokenBlacklistService.isBlacklisted(jti)) {
				log.debug("Token is blacklisted: {}", jti);
				return false;
			}

			return true;
		} catch (TokenException e) {
			return false;
		}
	}

	public void blacklistToken (String token, long remainingTtlSeconds) {
		try {
			Claims claims = parseToken(token);
			String jti = claims.getId();
			if (jti != null) {
				tokenBlacklistService.blacklistToken(jti, java.time.Duration.ofSeconds(remainingTtlSeconds));
				log.debug("Token blacklisted: {}", jti);
			}
		} catch (Exception e) {
			log.warn("Failed to blacklist token", e);
		}
	}

	public UUID getUserIdFromToken (String token) {
		Claims claims = parseToken(token);
		return UUID.fromString(claims.getSubject());
	}

	public String getEmailFromToken (String token) {
		Claims claims = parseToken(token);
		return claims.get("email", String.class);
	}

	@SuppressWarnings("unchecked")
	public Set<String> getRolesFromToken (String token) {
		Claims claims = parseToken(token);
		List<String> roles = claims.get("roles", List.class);
		return roles != null ? new HashSet<>(roles) : Collections.emptySet();
	}

	public AccountStatus getStatusFromToken (String token) {
		Claims claims = parseToken(token);
		String status = claims.get("status", String.class);
		if (status != null) {
			try {
				return AccountStatus.valueOf(status);
			} catch (IllegalArgumentException e) {
				return AccountStatus.ACTIVE;
			}
		}
		return AccountStatus.ACTIVE;
	}

	public String getJtiFromToken (String token) {
		Claims claims = parseToken(token);
		return claims.getId();
	}

	public Instant getExpirationFromToken (String token) {
		Claims claims = parseToken(token);
		return claims.getExpiration().toInstant();
	}

	public void rotateParser () {
		currentParser = buildParser(keyProvider.getPublicKey());
		rebuildPreviousParser();
		log.info("JWT parser rebuilt with new public key after key rotation");
	}

	public long getRemainingTtlSeconds (String token) {
		Instant expiration = getExpirationFromToken(token);
		Instant now = Instant.now();
		return Math.max(0, expiration.getEpochSecond() - now.getEpochSecond());
	}
}
