package com.miqu3iasg.banking.shared.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

	private static final String ENTITY_ACCOUNT = "ACCOUNT";

	private final AuditLogRepository auditLogRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordAccountCreated (
		UUID accountId,
		String accountNumber,
		String accountType,
		String documentSuffix
	) {
		String afterData = AccountAuditPayload.builder()
			.accountNumber(accountNumber)
			.type(accountType)
			.status("ACTIVE")
			.documentSuffix(documentSuffix)
			.build()
			.toJson();

		persist(AuditLog.of(
			ENTITY_ACCOUNT,
			accountId,
			AuditAction.CREATE,
			null,
			"Account opened: " + accountNumber,
			null,
			afterData
		));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordStatusChange (
		UUID accountId,
		String accountNumber,
		String fromStatus,
		String toStatus,
		UUID actorId,
		String reason
	) {
		String beforeData = AccountStatusPayload.builder()
			.accountNumber(accountNumber)
			.status(fromStatus)
			.build()
			.toJson();

		String afterData = AccountStatusPayload.builder()
			.accountNumber(accountNumber)
			.status(toStatus)
			.reason(reason)
			.build()
			.toJson();

		String description = "Status changed %s → %s: %s"
			.formatted(
				fromStatus,
				toStatus,
				reason
			);

		persist(AuditLog.of(
			ENTITY_ACCOUNT,
			accountId,
			AuditAction.STATUS_CHANGE,
			actorId,
			description,
			beforeData,
			afterData
		));
	}

	private void persist (AuditLog entry) {
		auditLogRepository.save(entry);

		log.debug(
			"Audit recorded: action={}, entity={}, entityId={}",
			entry.getAction(), entry.getEntityType(), entry.getEntityId()
		);
	}
}
