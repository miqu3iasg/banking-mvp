package com.miqu3iasg.banking_mvp.auth.support;

import com.miqu3iasg.banking.auth.config.AuthProperties;
import com.miqu3iasg.banking.auth.domain.*;
import com.miqu3iasg.banking.auth.repository.ApiKeyRepository;
import com.miqu3iasg.banking.auth.repository.LoginAttemptRepository;
import com.miqu3iasg.banking.auth.repository.RoleRepository;
import com.miqu3iasg.banking.auth.repository.UserRepository;
import com.miqu3iasg.banking.auth.security.JwtService;
import com.miqu3iasg.banking.auth.security.RateLimitingFilter;
import com.miqu3iasg.banking.auth.security.RefreshTokenService;
import com.miqu3iasg.banking.auth.service.ApiKeyService;
import com.miqu3iasg.banking.auth.service.HashingService;
import com.miqu3iasg.banking.auth.service.PasswordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AuthTestDataFactory {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final List<UUID> createdUserIds = new CopyOnWriteArrayList<>();
    private final List<UUID> createdApiKeyIds = new CopyOnWriteArrayList<>();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    // Add this method to the class
    public ApiKeyRepository getApiKeyRepository() {
        return apiKeyRepository;
    }

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private HashingService hashingService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private AuthProperties authProperties;

    public User createUser(AccountStatus status) {
        String email = "user-" + UUID.randomUUID() + "@test.com";
        String password = "TestP@ssw0rd123!";

        User user = new User();
        user.setEmail(email);
        user.setEmailHash(hashingService.emailHash(email));
        user.setPasswordHash(passwordService.encode(password));
        user.setStatus(status);
        user.setEmailVerified(status != AccountStatus.PENDING_VERIFICATION);
        user.setMfaEnabled(false);
        user.setFailedLoginAttempts(0);
        user.setConsentEmail(true);
        user.setConsentTimestamp(Instant.now());

        Set<RoleEntity> roles = new HashSet<>();
        roleRepository.findByName(Role.ROLE_USER).ifPresent(roles::add);
        user.setRoles(roles);

        user = userRepository.save(user);
        createdUserIds.add(user.getId());
        return user;
    }

    public User createUserWithRoles(Set<Role> roleEnums) {
        User user = createUser(AccountStatus.ACTIVE);
        Set<RoleEntity> roles = new HashSet<>();
        for (Role roleEnum : roleEnums) {
            roleRepository.findByName(roleEnum).ifPresent(roles::add);
        }
        user.setRoles(roles);
        return userRepository.save(user);
    }

    public User createUserWithMfa() {
        User user = createUser(AccountStatus.ACTIVE);
        user.setMfaEnabled(true);
        byte[] secretBytes = new byte[20];
        SECURE_RANDOM.nextBytes(secretBytes);
        user.setMfaSecret(Base64.getEncoder().encodeToString(secretBytes));
        return userRepository.save(user);
    }

    public String createRefreshToken(UUID userId) {
        String ipHash = hashingService.ipHash("127.0.0.1");
        String userAgentHash = hashingService.userAgentHash("test-agent");
        var token = refreshTokenService.createRefreshToken(userId, ipHash, userAgentHash, "test-fingerprint");
        return token.entity().getId().toString();
    }

    public String createApiKey(Set<Permission> scopes) {
        User owner = createUser(AccountStatus.ACTIVE);
        var result = apiKeyService.createApiKey(
            owner.getId(),
            "test-key-" + UUID.randomUUID(),
            "Test API key",
            scopes != null ? scopes : EnumSet.noneOf(Permission.class),
            new HashSet<>(),
            Instant.now().plusSeconds(86400 * 30)
        );
        createdApiKeyIds.add(result.apiKey().getId());

        return result.rawKey();
    }

    public String generateEmail() {
        return "user-" + UUID.randomUUID() + "@test.com";
    }

    public String generatePassword() {
        return "TestP@ssw0rd123!";
    }

    public String generateRawApiKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "bk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public void cleanup() {
        rateLimitingFilter.resetForTesting(hashingService.ipHash("127.0.0.1"));
        loginAttemptRepository.deleteAll();
        createdUserIds.forEach(id -> {
            try {
                userRepository.deleteById(id);
            } catch (Exception ignored) {
            }
        });
        createdApiKeyIds.forEach(id -> {
            try {
                apiKeyRepository.deleteById(id);
            } catch (Exception ignored) {
            }
        });
        createdUserIds.clear();
        createdApiKeyIds.clear();
    }
}
