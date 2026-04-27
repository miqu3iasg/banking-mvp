package com.miqu3iasg.banking.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles", indexes = {
    @Index(name = "idx_roles_name", columnList = "name", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

	@Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30)
    private Role name;

    public Role getName() {
        return name;
    }

    @Column(length = 200)
    private String description;

    @ElementCollection(targetClass = Permission.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Permission> permissions = EnumSet.noneOf(Permission.class);

	@Column(name = "mfa_required", nullable = false)
    @Builder.Default
    private boolean mfaRequired = false;

    public boolean isMfaRequired() {
        return mfaRequired;
    }
}
