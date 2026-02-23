package com.miqu3iasg.banking.shared.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
	List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc (String entityType, UUID entityId);
}
