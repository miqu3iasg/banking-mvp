package com.miqu3iasg.banking.shared.audit;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "audit_log", indexes = {
	@Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
	@Index(name = "idx_audit_actor", columnList = "actor_id"),
	@Index(name = "idx_audit_created_at", columnList = "created_at")
})
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "entity_type", nullable = false, length = 50)
	private String entityType;

	@Column(name = "entity_id", nullable = false)
	private UUID entityId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 20)
	private AuditAction action;

	/**
	 * The user or system component that triggered the change
	 */
	@Column(name = "actor_id")
	private UUID actorId;

	@Column(name = "description", length = 500)
	private String description;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "before_data", columnDefinition = "jsonb")
	private String beforeData;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "after_data", columnDefinition = "jsonb")
	private String afterData;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AuditLog () { }

	public static AuditLog of (
		String entityType, UUID entityId,
		AuditAction action,
		UUID actorId,
		String description,
		String beforeData,
		String afterData
	) {
		AuditLog entry = new AuditLog();
		entry.entityType = entityType;
		entry.entityId = entityId;
		entry.action = action;
		entry.actorId = actorId;
		entry.description = description;
		entry.beforeData = beforeData;
		entry.afterData = afterData;
		entry.createdAt = Instant.now();
		return entry;
	}
}
