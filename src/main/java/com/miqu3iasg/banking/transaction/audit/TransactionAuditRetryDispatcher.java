package com.miqu3iasg.banking.transaction.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miqu3iasg.banking.shared.domain.Money;
import com.miqu3iasg.banking.shared.outbox.OutboxEvent;
import com.miqu3iasg.banking.shared.outbox.OutboxEventDispatcher;
import com.miqu3iasg.banking.transaction.domain.TransactionStatus;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import com.miqu3iasg.banking.transaction.service.TransactionCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Dispatcher responsible for processing {@code TRANSACTION_AUDIT_RETRY} events from the Outbox.
 * This ensures that if the initial audit recording fails, the system will reliably retry
 * the operation using an exponential back-off strategy managed by the {@link com.miqu3iasg.banking.shared.outbox.OutboxProcessor}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionAuditRetryDispatcher implements OutboxEventDispatcher {

	private final ObjectMapper objectMapper;
	private final TransactionAuditService auditService;

	@Override
	public String eventType () {
		return "TRANSACTION_AUDIT_RETRY";
	}

	/**
	 * Processes the outbox event by deserializing the payload into a {@link TransactionAuditRetryPayload},
	 * reconstructing the original {@link TransactionCompletedEvent}, and delegating to the {@link TransactionAuditService}.
	 *
	 * @param event the outbox event to dispatch
	 * @throws Exception if deserialization fails or the audit service encounters an error, triggering a retry
	 */
	@Override
	public void dispatch (OutboxEvent event) throws Exception {
		log.info("Dispatching audit retry event: id={} aggregateId={}", event.getId(), event.getAggregateId());

		TransactionAuditRetryPayload payload = objectMapper.readValue(
			event.getPayload(),
			TransactionAuditRetryPayload.class
		);

		if (payload.status() != null && "PAYLOAD_SERIALIZATION_FAILED".equals(payload.status())) {
			throw new IllegalArgumentException(
				"Cannot dispatch event with PAYLOAD_SERIALIZATION_FAILED status: id=" + event.getId()
			);
		}

		if (payload.schemaVersion() != 1) {
			throw new IllegalArgumentException(
				"Unsupported payload schema version: " + payload.schemaVersion() + " for event id=" + event.getId()
			);
		}

		TransactionCompletedEvent reconstructedEvent = reconstructEvent(payload);

		switch (reconstructedEvent.type()) {
			case CREDIT -> auditService.recordDeposit(reconstructedEvent);
			case DEBIT -> auditService.recordWithdrawal(reconstructedEvent);
			case TRANSFER_DEBIT, TRANSFER_CREDIT -> auditService.recordTransferLeg(reconstructedEvent);
			case PIX_DEBIT, PIX_CREDIT -> auditService.recordPixLeg(reconstructedEvent);
			case BOLETO_PAYMENT -> auditService.recordBoletoPayment(reconstructedEvent);
		}

		log.debug("Successfully processed audit retry for transactionId={}", payload.transactionId());
	}

	private TransactionCompletedEvent reconstructEvent (TransactionAuditRetryPayload payload) {
		return new TransactionCompletedEvent(
			UUID.fromString(payload.transactionId()),
			UUID.fromString(payload.accountId()),
			payload.counterpartAccountId() != null ? UUID.fromString(payload.counterpartAccountId()) : null,
			TransactionType.valueOf(payload.type()),
			TransactionStatus.valueOf(payload.status()),
			Money.of(new BigDecimal(payload.amount()), payload.currency()),
			payload.description(),
			payload.referenceId(),
			Instant.parse(payload.occurredAt())
		);
	}
}
