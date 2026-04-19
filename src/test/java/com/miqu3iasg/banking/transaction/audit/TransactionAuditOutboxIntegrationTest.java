package com.miqu3iasg.banking.transaction.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miqu3iasg.banking.shared.outbox.OutboxEvent;
import com.miqu3iasg.banking.shared.outbox.OutboxProcessor;
import com.miqu3iasg.banking.shared.outbox.OutboxRepository;
import com.miqu3iasg.banking.shared.outbox.OutboxStatus;
import com.miqu3iasg.banking.shared.audit.AuditLogRepository;
import com.miqu3iasg.banking.transaction.domain.TransactionStatus;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import com.miqu3iasg.banking.transaction.service.TransactionCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class TransactionAuditOutboxIntegrationTest {

	@Autowired
	private OutboxProcessor outboxProcessor;

	@Autowired
	private OutboxRepository outboxRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private TransactionAuditService transactionAuditService;

	@BeforeEach
	public void setup() {
		outboxRepository.deleteAll();
		auditLogRepository.deleteAll();
	}

	@Test
	public void poolAndProcess_whenPayloadIsValid_marksEventProcessedAndCallsAuditService() throws Exception {
		// Arrange
		UUID txId = UUID.randomUUID();
		TransactionAuditRetryPayload payload = new TransactionAuditRetryPayload(
			1,
			txId.toString(),
			UUID.randomUUID().toString(),
			null,
			TransactionType.CREDIT.name(),
			TransactionStatus.COMPLETED.name(),
			"100.00",
			"BRL",
			"REF123",
			Instant.now().toString(),
			"Test deposit"
		);

		OutboxEvent event = OutboxEvent.of(
			"TRANSACTION_AUDIT_RETRY",
			txId.toString(),
			objectMapper.writeValueAsString(payload)
		);

		outboxRepository.save(event);

		// Act
		outboxProcessor.poolAndProcess();

		// Assert
		verify(transactionAuditService, times(1)).recordDeposit(any(TransactionCompletedEvent.class));

		OutboxEvent processedEvent = outboxRepository.findById(event.getId()).orElseThrow();
		assertEquals(OutboxStatus.PROCESSED, processedEvent.getStatus());
	}

	@Test
	public void poolAndProcess_whenServiceFails_marksEventPendingAndIncrementsAttempts() throws Exception {
		// Arrange
		UUID txId = UUID.randomUUID();
		TransactionAuditRetryPayload payload = new TransactionAuditRetryPayload(
			1,
			txId.toString(),
			UUID.randomUUID().toString(),
			null,
			TransactionType.CREDIT.name(),
			TransactionStatus.COMPLETED.name(),
			"100.00",
			"BRL",
			"REF123",
			Instant.now().toString(),
			"Test deposit"
		);

		OutboxEvent event = OutboxEvent.of(
			"TRANSACTION_AUDIT_RETRY",
			txId.toString(),
			objectMapper.writeValueAsString(payload)
		);

		outboxRepository.save(event);

		doThrow(new RuntimeException("Transient error")).when(transactionAuditService).recordDeposit(any());

		// Act
		outboxProcessor.poolAndProcess();

		// Assert
		OutboxEvent updatedEvent = outboxRepository.findById(event.getId()).orElseThrow();
		assertEquals(OutboxStatus.PENDING, updatedEvent.getStatus());
		assertEquals(1, updatedEvent.getAttempts());
	}

	@Test
	public void poolAndProcess_whenPayloadIsMalformed_marksEventFailed() throws Exception {
		// Arrange
		OutboxEvent event = OutboxEvent.of(
			"TRANSACTION_AUDIT_RETRY",
			UUID.randomUUID().toString(),
			"{ \"invalid\": \"json\" }"
		);

		outboxRepository.save(event);

		// Act
		outboxProcessor.poolAndProcess();

		// Assert
		OutboxEvent processedEvent = outboxRepository.findById(event.getId()).orElseThrow();
		assertEquals(OutboxStatus.FAILED, processedEvent.getStatus());
	}

	@Test
	public void poolAndProcess_whenAttemptsExhausted_marksEventFailed() throws Exception {
		// Arrange
		UUID txId = UUID.randomUUID();
		TransactionAuditRetryPayload payload = new TransactionAuditRetryPayload(
			1,
			txId.toString(),
			UUID.randomUUID().toString(),
			null,
			TransactionType.CREDIT.name(),
			TransactionStatus.COMPLETED.name(),
			"100.00",
			"BRL",
			"REF123",
			Instant.now().toString(),
			"Test deposit"
		);

		OutboxEvent event = OutboxEvent.of(
			"TRANSACTION_AUDIT_RETRY",
			txId.toString(),
			objectMapper.writeValueAsString(payload)
		);

		outboxRepository.save(event);

		doThrow(new RuntimeException("Permanent error")).when(transactionAuditService).recordDeposit(any());

		// Act
		// Simulate processing until exhausted
		for (int i = 0; i < 5; i++) {
			outboxProcessor.poolAndProcess();
		}

		// Assert
		OutboxEvent processedEvent = outboxRepository.findById(event.getId()).orElseThrow();
		assertEquals(OutboxStatus.FAILED, processedEvent.getStatus());
	}
}
