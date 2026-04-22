package com.miqu3iasg.banking.transaction.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miqu3iasg.banking.shared.outbox.OutboxEvent;
import com.miqu3iasg.banking.shared.outbox.OutboxRepository;
import com.miqu3iasg.banking.transaction.audit.TransactionAuditPayload;
import com.miqu3iasg.banking.transaction.audit.TransactionAuditRetryPayload;
import com.miqu3iasg.banking.transaction.audit.TransactionAuditService;
import com.miqu3iasg.banking.transaction.service.TransactionCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventListener {

	private static final int PAYLOAD_SCHEMA_VERSION = 1;
	private static final String AUDIT_RETRY_EVENT_TYPE = "TRANSACTION_AUDIT_RETRY";
	private static final String PAYLOAD_SERIALIZATION_FAILED_TEMPLATE = """
			{
				"schemaVersion": %d,
				"transactionId": "%s",
				"status": "PAYLOAD_SERIALIZATION_FAILED"
			}
			""";

	private final OutboxRepository outboxRepository;
	private final ObjectMapper objectMapper;
	private final TransactionAuditService auditService;

	@Async
	@EventListener(
		classes = TransactionCompletedEvent.class
	)
	public void onTransactionCompleted (TransactionCompletedEvent event) {
		log.debug(
			"Processing TransactionCompletedEvent: transactionId={} type={} status={} occurredAt={}",
			event.transactionId(),
			event.type(),
			event.status(),
			event.occurredAt()
		);

		try {
			switch (event.type()) {
				case CREDIT -> auditService.recordDeposit(event);
				case DEBIT -> auditService.recordWithdrawal(event);
				case TRANSFER_DEBIT, TRANSFER_CREDIT -> auditService.recordTransferLeg(event);
				case PIX_DEBIT, PIX_CREDIT -> auditService.recordPixLeg(event);
				case BOLETO_PAYMENT -> auditService.recordBoletoPayment(event);
			}
		} catch (Exception ex) {
			handleFailure(event, ex);
		}
	}

	public void handleFailure (TransactionCompletedEvent event, Exception cause) {
		log.error(
			"Audit write failed; enqueuing for Outbox retry: transactionId={} accountId={} type={} status={} referenceId={} cause={}",
			event.transactionId(),
			event.accountId(),
			event.type(),
			event.status(),
			event.referenceId(),
			cause.getMessage(),
			cause
		);

		try {
			outboxRepository.save(
				OutboxEvent.of(
					AUDIT_RETRY_EVENT_TYPE,
					event.transactionId().toString(),
					buildPayload(event)
				)
			);
		} catch (Exception outboxEx) {
			log.error(
				"CRITICAL — Outbox enqueue failed for audit retry: transactionId={} type={} status={} cause={}",
				event.transactionId(),
				event.type(),
				event.status(),
				outboxEx.getMessage(),
				outboxEx
			);
		}
	}

	private String buildPayload (TransactionCompletedEvent event) {
		try {
			return objectMapper.writeValueAsString(new TransactionAuditRetryPayload(
				PAYLOAD_SCHEMA_VERSION,
				event.transactionId().toString(),
				event.accountId().toString(),
				event.counterpartAccountId() != null ? event.counterpartAccountId().toString() : null,
				event.type().name(),
				event.status().name(),
				event.amount().amount().toPlainString(),
				event.amount().currency().getCurrencyCode(),
				event.referenceId(),
				event.occurredAt().toString(),
				event.description()
			));
		} catch (Exception e) {
			log.error(
				"Failed to serialise Outbox retry payload for transactionId={}: {}",
				event.transactionId(),
				e.getMessage(),
				e
			);

			return PAYLOAD_SERIALIZATION_FAILED_TEMPLATE
				.formatted(
					PAYLOAD_SCHEMA_VERSION,
					event.transactionId()
				);
		}
	}
}
