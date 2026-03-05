package com.miqu3iasg.banking.pix.domain;

import com.miqu3iasg.banking.shared.domain.AuditableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "pix_keys")
public class PixKey extends AuditableEntity {

	@Column(name = "account_id", nullable = false, updatable = false)
	private UUID accountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10, updatable = false)
	private PixKeyType type;

	/**
	 * The key value itself — CPF digits, email address, phone in E.164, or random UUID.
	 * Max 77 chars per BACEN spec.
	 */
	@Column(nullable = false, length = 77, updatable = false)
	private String value;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private PixKeyStatus status;

	public static PixKey register(UUID accountId, PixKeyType type, String rawValue) {
		var key = new PixKey();
		key.accountId = accountId;
		key.type = type;
		key.value = normalize(type, rawValue);
		key.status = PixKeyStatus.ACTIVE;
		return key;
	}

	public void delete() {
		this.status = PixKeyStatus.DELETED;
	}

	public boolean isActive() {
		return status == PixKeyStatus.ACTIVE;
	}

	/**
	 * Normalises key values to a canonical form before persistence.
	 * CPF/CNPJ: strip punctuation; EMAIL: lowercase; PHONE: ensure + prefix.
	 */
	private static String normalize(PixKeyType type, String value) {
		if (value == null) throw new IllegalArgumentException("PIX key value must not be null");
		return switch (type) {
			case CPF, CNPJ -> value.replaceAll("[^0-9]", "");
			case EMAIL -> value.trim().toLowerCase();
			case PHONE -> value.trim().startsWith("+") ? value.trim() : "+" + value.trim().replaceAll("[^0-9]", "");
			case RANDOM -> value.trim();
		};
	}
}
