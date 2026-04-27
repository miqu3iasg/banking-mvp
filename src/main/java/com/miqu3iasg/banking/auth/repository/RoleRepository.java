package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.Role;
import com.miqu3iasg.banking.auth.domain.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    Optional<RoleEntity> findByName(Role name);

    boolean existsByName(Role name);
}
