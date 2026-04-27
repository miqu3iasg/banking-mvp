package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailHash(String emailHash);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") UUID id);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.email = :email")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.emailHash = :emailHash")
    Optional<User> findByEmailHashWithRoles(@Param("emailHash") String emailHash);

    List<User> findByStatus(AccountStatus status);

    @Query("SELECT u FROM User u WHERE u.passwordExpiresAt < :now")
    Page<User> findByPasswordExpired(@Param("now") Instant now, Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.status = :status")
    long countByStatus(@Param("status") AccountStatus status);

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :now WHERE u.id = :id")
    void updateLastLoginAt(@Param("id") UUID id, @Param("now") Instant now);
}
