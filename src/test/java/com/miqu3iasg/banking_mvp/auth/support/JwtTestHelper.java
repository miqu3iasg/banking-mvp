package com.miqu3iasg.banking_mvp.auth.support;

import com.miqu3iasg.banking.auth.config.AuthProperties;
import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.security.JwtKeyProvider;
import com.miqu3iasg.banking.auth.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtTestHelper {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtKeyProvider keyProvider;

    @Autowired
    private AuthProperties authProperties;

    public String generateValidToken(UUID userId, String email, Set<String> roles) {
        return jwtService.generateAccessToken(userId, email, roles, null, AccountStatus.ACTIVE);
    }

    public String generateValidToken(UUID userId, String email, Set<String> roles, Map<String, Object> claims) {
        return jwtService.generateAccessToken(userId, email, roles, claims, AccountStatus.ACTIVE);
    }

    public String generateExpiredToken(UUID userId, String email, Set<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor("test-expired-token-key-for-testing-purposes-only!!".getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId.toString())
            .claim("email", email)
            .claim("roles", new HashSet<>(roles))
            .issuer("test-issuer")
            .issuedAt(new Date(System.currentTimeMillis() - 2000))
            .expiration(new Date(System.currentTimeMillis() - 1000))
            .signWith(key)
            .compact();
    }

    public String generateTamperedToken(UUID userId, String email, Set<String> roles) {
        SecretKey wrongKey = Keys.hmacShaKeyFor("wrong-key-for-tampering-test-not-the-real-one!!".getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId.toString())
            .claim("email", email)
            .claim("roles", new HashSet<>(roles))
            .issuer("tampered-issuer")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 900000))
            .signWith(wrongKey)
            .compact();
    }

    public String generateAlgNoneToken(UUID userId, String email, Set<String> roles) {
        String header = "{\"alg\":\"none\",\"typ\":\"JWT\"}";
        String payload = String.format(
            "{\"sub\":\"%s\",\"email\":\"%s\",\"roles\":[\"%s\"],\"iat\":%d,\"exp\":%d}",
            userId, email, String.join("\",\"", roles),
            System.currentTimeMillis() / 1000,
            (System.currentTimeMillis() / 1000) + 900
        );
        String encodedHeader = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedHeader + "." + encodedPayload + ".";
    }

    public String generateHs256Token(UUID userId, String email, Set<String> roles) {
        SecretKey hmacKey = Keys.hmacShaKeyFor("test-hs256-key-for-algorithm-confusion-test!!".getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId.toString())
            .claim("email", email)
            .claim("roles", new HashSet<>(roles))
            .issuer("hs256-issuer")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 900000))
            .signWith(hmacKey)
            .compact();
    }

    public String generateTokenWithInflatedRoles(UUID userId, String email) {
        Set<String> inflatedRoles = Set.of("ROLE_USER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN");
        String validToken = jwtService.generateAccessToken(userId, email, Set.of("ROLE_USER"), null, AccountStatus.ACTIVE);
        String[] parts = validToken.split("\\.");
        String decodedPayload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tamperedPayload = decodedPayload.replace("\"ROLE_USER\"", "\"ROLE_SUPER_ADMIN\"");
        String tamperedEncoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8));
        return parts[0] + "." + tamperedEncoded + "." + parts[2];
    }

    public KeyPair generateTestKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA not available", e);
        }
    }
}
