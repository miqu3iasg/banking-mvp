package com.miqu3iasg.banking.shared.domain;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(updatable = false, nullable = false)
	private UUID id;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * The authenticated principal (UUID) who created this record.
	 * May be {@code null} for records created by scheduled jobs or system processes.
	 */
	@CreatedBy
	@Column(name = "created_by", updatable = false)
	private UUID createdBy;

	/**
	 * The authenticated principal (UUID) who last modified this record.
	 * May be {@code null} for records modified by scheduled jobs or system processes.
	 */
	@LastModifiedBy
	@Column(name = "updated_by")
	private UUID updatedBy;

	@Version
	@Column(nullable = false)
	private Long version = 0L;

	@Override
	public boolean equals (Object o) {
		if (this == o) return true;
		if (!(o instanceof AuditableEntity other)) return false;
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode () {
		return getClass().hashCode();
	}

	@Override
	public String toString () {
		return "%s[id=%s, version=%d]".formatted(getClass().getSimpleName(), id, version);
	}
}
