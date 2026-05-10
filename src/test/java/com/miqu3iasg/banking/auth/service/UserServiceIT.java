package com.miqu3iasg.banking.auth.service;

import com.miqu3iasg.banking.auth.domain.*;
import com.miqu3iasg.banking.auth.repository.*;
import com.miqu3iasg.banking.auth.exception.PasswordException;
import com.miqu3iasg.banking.auth.exception.RegistrationException;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("auth-test")
@Testcontainers
@Transactional
class UserServiceIT {

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
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void createUser_whenValid_thenCreatesUser() {
        String email = "user_" + UUID.randomUUID() + "@example.com";
        String password = "SecurePass123!";

        User user = userService.createUser(email, password, false);

        assertThat(user).isNotNull();
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
    }

    @Test
    void createUser_whenEmailAlreadyExists_thenThrowsException() {
        String email = "duplicate_" + UUID.randomUUID() + "@example.com";
        String password = "SecurePass123!";

        userService.createUser(email, password, false);

        assertThatThrownBy(() -> userService.createUser(email, password, false))
                .isInstanceOf(RegistrationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createUser_whenInvalidEmail_thenThrowsException() {
        String email = "invalid-email";
        String password = "SecurePass123!";

        assertThatThrownBy(() -> userService.createUser(email, password, false))
                .isInstanceOf(RegistrationException.class);
    }

    @Test
    void createUser_whenWeakPassword_thenThrowsException() {
        String email = "user_" + UUID.randomUUID() + "@example.com";
        String password = "weak";

        assertThatThrownBy(() -> userService.createUser(email, password, false))
                .isInstanceOf(PasswordException.class);
    }

    @Test
    void findByEmail_whenExists_thenReturnUser() {
        String email = "find_" + UUID.randomUUID() + "@example.com";
        String password = "SecurePass123!";

        userService.createUser(email, password, false);

        Optional<User> found = userService.findByEmail(email);

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo(email);
    }

    @Test
    void findByEmail_whenNotExists_thenReturnEmpty() {
        Optional<User> found = userService.findByEmail("nonexistent@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    void updateLastLogin_whenUserExists_thenUpdatesTimestamp() {
        String email = "login_" + UUID.randomUUID() + "@example.com";
        String password = "SecurePass123!";
        User user = userService.createUser(email, password, false);

        userService.updateLastLogin(user.getId());

        User updated = userService.findById(user.getId()).get();
        assertThat(updated.getLastLoginAt()).isNotNull();
    }

    @Test
    void suspendUser_whenValid_thenSuspendsUser() {
        String email = "suspend_" + UUID.randomUUID() + "@example.com";
        String password = "SecurePass123!";
        User user = userService.createUser(email, password, false);

        userService.suspendUser(user.getId(), "Test suspension");

        User suspended = userService.findById(user.getId()).get();
        assertThat(suspended.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(suspended.getSuspensionReason()).isEqualTo("Test suspension");
    }

    @Test
    void unsuspendUser_whenValid_thenRestoresUser() {
        String email = "unsuspend_" + UUID.randomUUID() + "@example.com";
        String password = "SecurePass123!";
        User user = userService.createUser(email, password, false);

        userService.suspendUser(user.getId(), "Test suspension");
        userService.unsuspendUser(user.getId());

        User restored = userService.findById(user.getId()).get();
        assertThat(restored.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(restored.getSuspensionReason()).isNull();
    }
}
