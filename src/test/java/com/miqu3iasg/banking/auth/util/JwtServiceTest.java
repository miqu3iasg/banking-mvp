package com.miqu3iasg.banking.auth.util;

import com.miqu3iasg.banking.auth.config.AuthProperties;
import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.security.JwtKeyProvider;
import com.miqu3iasg.banking.auth.security.JwtService;
import com.miqu3iasg.banking.auth.config.RedisConfig.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.KeyPairGenerator;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private JwtKeyProvider keyProvider;

    @Mock
    private AuthProperties authProperties;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private JwtService jwtService;

    private final UUID userId = UUID.randomUUID();
    private final String email = "user@example.com";
    private final Set<String> roles = Set.of("USER");
    private final AccountStatus status = AccountStatus.ACTIVE;

    @BeforeEach
    void setUp() throws Exception {
        // Mock AuthProperties JWT settings
        AuthProperties.Jwt jwt = mock(AuthProperties.Jwt.class);
        when(authProperties.getJwt()).thenReturn(jwt);
        when(jwt.getAudience()).thenReturn("test-audience");
        when(jwt.getIssuer()).thenReturn("test-issuer");
        when(jwt.getAccessTokenExpirySeconds()).thenReturn(3600);

        // Generate a real RSA key pair for signing and verification
        java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        java.security.KeyPair keyPair = keyGen.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();
        when(keyProvider.getKeyId()).thenReturn("test-key-id");
        when(keyProvider.getPrivateKey()).thenReturn(privateKey);
        when(keyProvider.getPublicKey()).thenReturn(publicKey);
        when(keyProvider.getPreviousKeyPair()).thenReturn(null);
        // Also mock getKeyId method that may be used elsewhere
        when(keyProvider.getKeyId()).thenReturn("test-key-id");
        // Ensure the parser is initialized after mocking
        jwtService.init();
    }

    @Test
    void generateAndParseToken_success() {
        String token = jwtService.generateAccessToken(userId, email, roles, Collections.emptyMap(), status);
        assertNotNull(token, "Generated token should not be null");

        Claims claims = jwtService.parseToken(token);
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals(email, claims.get("email", String.class));
        assertEquals(status.name(), claims.get("status", String.class));
    }

    @Test
    void validateToken_notBlacklisted_true() {
        String token = jwtService.generateAccessToken(userId, email, roles, Collections.emptyMap(), status);
        when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(false);
        assertTrue(jwtService.validateToken(token));
    }

    @Test
    void validateToken_blacklisted_false() {
        String token = jwtService.generateAccessToken(userId, email, roles, Collections.emptyMap(), status);
        when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(true);
        assertFalse(jwtService.validateToken(token));
    }
}
