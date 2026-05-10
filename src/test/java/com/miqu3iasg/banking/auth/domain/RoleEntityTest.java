package com.miqu3iasg.banking.auth.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoleEntityTest {

    @Test
    void builder_createsValidRole() {
        UUID id = UUID.randomUUID();
        String name = "ROLE_USER";
        String description = "Standard user role";

        RoleEntity role = RoleEntity.builder()
                .id(id)
                .name(Role.valueOf(name))
                .description(description)
                .mfaRequired(false)
                .build();

        assertEquals(id, role.getId());
        assertEquals(Role.ROLE_USER, role.getName());
        assertEquals(description, role.getDescription());
        assertFalse(role.isMfaRequired());
    }

    @Test
    void permissionsSet_containsAllPermissions() {
        var permissions = EnumSet.of(Permission.ACCOUNT_READ, Permission.USER_READ);

        RoleEntity role = RoleEntity.builder()
                .permissions(permissions)
                .build();

        assertEquals(2, role.getPermissions().size());
        assertTrue(role.getPermissions().contains(Permission.ACCOUNT_READ));
        assertTrue(role.getPermissions().contains(Permission.USER_READ));
    }

    @Test
    void mfaRequired_returnsCorrectValue() {
        RoleEntity roleWithMfa = RoleEntity.builder().mfaRequired(true).build();
        RoleEntity roleWithoutMfa = RoleEntity.builder().mfaRequired(false).build();

        assertTrue(roleWithMfa.isMfaRequired());
        assertFalse(roleWithoutMfa.isMfaRequired());
    }
}
