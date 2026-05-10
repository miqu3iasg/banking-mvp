package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.Permission;
import com.miqu3iasg.banking.auth.domain.Role;
import com.miqu3iasg.banking.auth.domain.RoleEntity;
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

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("auth-test")
@Testcontainers
class RoleRepositoryIT {

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
    private RoleRepository roleRepository;

    @Test
    @Transactional
    void save_persistsRole() {
        String uniqueName = "ROLE_TEST_" + UUID.randomUUID().toString().replace("-", "_").substring(0, 16);
        RoleEntity role = createRole(Role.ROLE_USER, uniqueName);

        RoleEntity saved = roleRepository.save(role);

        Optional<RoleEntity> found = roleRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo(Role.ROLE_USER);
    }

    @Test
    @Transactional
    void findByName_returnsRole() {
        String uniqueName = "ROLE_FIND_" + UUID.randomUUID().toString().replace("-", "_").substring(0, 16);
        createRole(Role.ROLE_USER, uniqueName);

        Optional<RoleEntity> found = roleRepository.findByName(Role.ROLE_USER);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo(Role.ROLE_USER);
    }

    @Test
    @Transactional
    void findByName_whenNotFound_returnsEmpty() {
        Optional<RoleEntity> found = roleRepository.findByName(Role.ROLE_ADMIN);

        assertThat(found).isEmpty();
    }

    @Test
    @Transactional
    void existsByName_returnsTrueWhenRoleExists() {
        String uniqueName = "ROLE_EXISTS_" + UUID.randomUUID().toString().replace("-", "_").substring(0, 16);
        createRole(Role.ROLE_USER, uniqueName);

        boolean exists = roleRepository.existsByName(Role.ROLE_USER);

        assertThat(exists).isTrue();
    }

    @Test
    @Transactional
    void existsByName_returnsFalseWhenRoleNotExists() {
        boolean exists = roleRepository.existsByName(Role.ROLE_ADMIN);

        assertThat(exists).isFalse();
    }

    @Test
    @Transactional
    void findAll_returnsAllRoles() {
        String uniqueName1 = "ROLE_ALL1_" + UUID.randomUUID().toString().replace("-", "_").substring(0, 16);
        String uniqueName2 = "ROLE_ALL2_" + UUID.randomUUID().toString().replace("-", "_").substring(0, 16);
        createRole(Role.ROLE_USER, uniqueName1);
        createRole(Role.ROLE_ADMIN, uniqueName2);

        Iterable<RoleEntity> roles = roleRepository.findAll();

        assertThat(roles).hasSize(2);
    }

    @Test
    @Transactional
    void update_modifiesRole() {
        String uniqueName = "ROLE_UPDATE_" + UUID.randomUUID().toString().replace("-", "_").substring(0, 16);
        RoleEntity role = createRole(Role.ROLE_USER, uniqueName);

        role.setDescription("Updated description");
        roleRepository.save(role);

        Optional<RoleEntity> found = roleRepository.findById(role.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getDescription()).isEqualTo("Updated description");
    }

    private RoleEntity createRole(String uniqueName) {
        return createRole(Role.ROLE_USER, uniqueName);
    }

    private RoleEntity createRole(Role roleName, String uniqueName) {
        RoleEntity role = RoleEntity.builder()
                .name(roleName)
                .description("Test role " + uniqueName)
                .permissions(EnumSet.of(Permission.ACCOUNT_READ))
                .mfaRequired(false)
                .build();
        return roleRepository.save(role);
    }
}
