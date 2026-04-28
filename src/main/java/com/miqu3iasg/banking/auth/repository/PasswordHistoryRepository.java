package com.miqu3iasg.banking.auth.repository;

import com.miqu3iasg.banking.auth.domain.PasswordHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, UUID> {

    List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(UUID userId);

    default List<PasswordHistory> findRecentByUserId(UUID userId, int limit) {
        return findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, limit))
            .getContent();
    }

    @Query("SELECT ph FROM PasswordHistory ph WHERE ph.userId = :userId ORDER BY ph.createdAt DESC")
    Page<PasswordHistory> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    @Modifying
    @Query(value = """
        DELETE FROM password_history
        WHERE user_id = :userId
        AND id NOT IN (
            SELECT id FROM password_history
            WHERE user_id = :userId
            ORDER BY created_at DESC
            LIMIT :keepCount
        )
        """, nativeQuery = true)
    int pruneOldPasswords(@Param("userId") UUID userId, @Param("keepCount") int keepCount);
}
