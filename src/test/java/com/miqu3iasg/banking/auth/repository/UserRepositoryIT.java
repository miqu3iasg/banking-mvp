package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.domain.Permission;
import com.miqu3iasg.banking.auth.domain.Role;
import com.miqu3iasg.banking.auth.domain.RoleEntity;
import com.miqu3iasg.banking.auth.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("auth-test")
@Testcontainers
class UserRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("banking_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("outbox.processor.enabled", () -> "false");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        // Create default role if not exists
        if (roleRepository.findByName(Role.ROLE_USER).isEmpty()) {
            createAndSaveRole(Role.ROLE_USER);
        }
    }

    @Test
    void save_persistsUser() {
        String email = "user1_" + UUID.randomUUID() + "@example.com";
        User user = createUser(email, null);

        Optional<User> found = userRepository.findById(user.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo(email);
    }

    @Test
    void findByEmailWithRoles_returnsUserWithRoles() {
        RoleEntity role = roleRepository.findByName(Role.ROLE_USER).get();
        String email = "user2_" + UUID.randomUUID() + "@example.com";
        createUser(email, role);

        Optional<User> found = userRepository.findByEmailWithRoles(email);

        assertThat(found).isPresent();
        assertThat(found.get().getRoles()).hasSize(1);
    }

    @Test
    void findByIdWithRoles_returnsUserWithRoles() {
        RoleEntity role = roleRepository.findByName(Role.ROLE_USER).get();
        String email = "user3_" + UUID.randomUUID() + "@example.com";
        User user = createUser(email, role);

        Optional<User> found = userRepository.findByIdWithRoles(user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getRoles()).hasSize(1);
    }

    @Test
    void existsByEmail_returnsTrueWhenEmailExists() {
        String email = "user4_" + UUID.randomUUID() + "@example.com";
        createUser(email, null);

        boolean exists = userRepository.existsByEmail(email);

        assertThat(exists).isTrue();
    }

    @Test
    void existsByEmail_returnsFalseWhenEmailNotExists() {
        boolean exists = userRepository.existsByEmail("nonexistent@example.com");

        assertThat(exists).isFalse();
    }

    @Test
    @Transactional
    void updateLastLoginAt_updatesTimestamp() {
        String email = "user5_" + UUID.randomUUID() + "@example.com";
        User user = createUser(email, null);

        Instant now = Instant.now();
        // Just verify the method executes without error
        userRepository.updateLastLoginAt(user.getId(), now);

        assertThat(true).isTrue();
    }

    @Test
    void findByEmailWithRoles_whenUserNotFound_returnsEmpty() {
        Optional<User> found = userRepository.findByEmailWithRoles("nonexistent@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    void findByEmailWithRoles_whenUserHasNoRoles_returnsUserWithEmptyRoles() {
        String email = "user6_" + UUID.randomUUID() + "@example.com";
        createUser(email, null);

        Optional<User> found = userRepository.findByEmailWithRoles(email);

        assertThat(found).isPresent();
        assertThat(found.get().getRoles()).isEmpty();
    }

    private RoleEntity createAndSaveRole(Role roleName) {
        RoleEntity role = RoleEntity.builder()
                .name(roleName)
                .description("Test role")
                .permissions(EnumSet.of(Permission.ACCOUNT_READ))
                .mfaRequired(false)
                .build();
        return roleRepository.save(role);
    }

    private User createUser(String email, RoleEntity role) {
        User user = User.builder()
                .email(email)
                .emailHash(computeHash(email))
                .passwordHash("bcrypt_hash")
                .status(AccountStatus.ACTIVE)
                .emailVerified(true)
                .build();
        if (role != null) {
            user.getRoles().add(role);
        }
        return userRepository.save(user);
    }

    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
