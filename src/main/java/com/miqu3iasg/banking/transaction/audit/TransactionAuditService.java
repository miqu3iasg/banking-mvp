package com.miqu3iasg.banking.transaction.audit;

import com.miqu3iasg.banking.shared.audit.AuditAction;
import com.miqu3iasg.banking.shared.audit.AuditLog;
import com.miqu3iasg.banking.shared.audit.AuditLogRepository;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import com.miqu3iasg.banking.transaction.service.TransactionCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionAuditService {

	private static final String ENTITY_TYPE = "TRANSACTION";

	private final AuditLogRepository auditLogRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordDeposit (TransactionCompletedEvent event) {
		record(event, "Deposit of %s %s on account %s [%s]".formatted(
			event.amount().amount().toPlainString(),
			event.amount().currency().getCurrencyCode(),
			event.accountId(),
			event.status()
		));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordWithdrawal (TransactionCompletedEvent event) {
		record(event, "Withdrawal of %s %s from account %s [%s]".formatted(
			event.amount().amount().toPlainString(),
			event.amount().currency().getCurrencyCode(),
			event.accountId(),
			event.status()
		));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordTransferLeg (TransactionCompletedEvent event) {
		String direction = event.type() == TransactionType.TRANSFER_DEBIT ? "debit from" : "credit to";

		record(event, "Transfer %s account %s of %s %s (ref: %s) [%s]".formatted(
			direction,
			event.accountId(),
			event.amount().amount().toPlainString(),
			event.amount().currency().getCurrencyCode(),
			event.referenceId(),
			event.status()
		));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordPixLeg (TransactionCompletedEvent event) {
		String direction = event.type() == TransactionType.PIX_DEBIT ? "sent from" : "received on";

		record(event, "PIX %s account %s of %s %s (ref: %s) [%s]".formatted(
			direction,
			event.accountId(),
			event.amount().amount().toPlainString(),
			event.amount().currency().getCurrencyCode(),
			event.referenceId(),
			event.status()
		));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordBoletoPayment (TransactionCompletedEvent event) {
		record(event, "Boleto payment of %s %s from account %s (ref: %s) [%s]".formatted(
			event.amount().amount().toPlainString(),
			event.amount().currency().getCurrencyCode(),
			event.accountId(),
			event.referenceId(),
			event.status()
		));
	}

	private void record (TransactionCompletedEvent event, String description) {
		String payload = TransactionAuditPayload.builder()
			.transactionId(event.transactionId().toString())
			.accountId(event.accountId().toString())
			.counterpartAccountId(
				event.counterpartAccountId() != null ? event.counterpartAccountId().toString() : null
			)
			.type(event.type().name())
			.status(event.status().name())
			.amount(event.amount().amount().toPlainString())
			.currency(event.amount().currency().getCurrencyCode())
			.referenceId(event.referenceId())
			.description(event.description())
			.build()
			.toJson();

		AuditLog entry = AuditLog.of(
			ENTITY_TYPE,
			event.transactionId(),
			AuditAction.from(event.type(), event.status()),
			null,
			description,
			null,
			payload
		);

		auditLogRepository.save(entry);

		log.debug(
			"Transaction audit recorded: action={} entityId={} status={}",
			entry.getAction(),
			entry.getEntityId(),
			event.status()
		);
	}
}
