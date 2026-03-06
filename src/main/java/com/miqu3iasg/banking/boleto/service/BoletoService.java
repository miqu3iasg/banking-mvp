package com.miqu3iasg.banking.boleto.service;

import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.boleto.api.dto.IssueBoletoRequest;
import com.miqu3iasg.banking.boleto.api.dto.IssueBoletoResponse;
import com.miqu3iasg.banking.boleto.config.EfiBoletoProperties;
import com.miqu3iasg.banking.boleto.domain.Boleto;
import com.miqu3iasg.banking.boleto.domain.BoletoStatus;
import com.miqu3iasg.banking.boleto.exception.BoletoNotFoundException;
import com.miqu3iasg.banking.boleto.gateway.BoletoGateway;
import com.miqu3iasg.banking.boleto.gateway.BoletoIssuanceRequest;
import com.miqu3iasg.banking.boleto.gateway.BoletoIssuanceResponse;
import com.miqu3iasg.banking.boleto.metrics.BoletoMetrics;
import com.miqu3iasg.banking.boleto.repository.BoletoRepository;
import com.miqu3iasg.banking.shared.domain.Money;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import com.miqu3iasg.banking.shared.idempotency.IdempotencyService;
import com.miqu3iasg.banking.transaction.domain.Transaction;
import com.miqu3iasg.banking.transaction.repository.TransactionRepository;
import com.miqu3iasg.banking.transaction.service.TransactionCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoletoService {
	private static final String OPERATION_TYPE_ISSUE = "BOLETO_ISSUE";
	private static final String OPERATION_TYPE_WEBHOOK = "BOLETO_WEBHOOK";

	private static final String PROVIDER_STATUS_PAID = "paid";

	private final BoletoRepository boletoRepository;
	private final AccountRepository accountRepository;
	private final TransactionRepository transactionRepository;
	private final BoletoGateway boletoGateway;
	private final IdempotencyService idempotencyService;
	private final EfiBoletoProperties props;
	private final BoletoMetrics metrics;
	private final ApplicationEventPublisher eventPublisher;

	public IssueBoletoResponse issue (UUID accountId, IssueBoletoRequest request, String idempotencyKey) {
		return idempotencyService
			.findCachedResponse(idempotencyKey, IssueBoletoResponse.class)
			.map(cached -> {
				log.debug("Boleto issuance idempotency HIT: key=[{}]", idempotencyKey);
				return cached;
			})
			.orElseGet(() -> executeIssuance(accountId, request, idempotencyKey));

	}

	@Transactional
	private IssueBoletoResponse executeIssuance (UUID accountId, IssueBoletoRequest request, String idempotencyKey) {
		return metrics.timeGatewayCall("issuance", () -> {
			accountRepository.findById(accountId)
				.orElseThrow(() -> new AccountNotFoundException(accountId));

			String normalizedDocument = normalizeDocument(request.payerDocument());

			Boleto boleto = Boleto.issue(
				accountId,
				request.payerName(),
				request.payerDocument(),
				request.amount(),
				request.dueDate(),
				request.description()
			);

			BoletoIssuanceRequest gatewayRequest = BoletoIssuanceRequest
				.from(boleto, normalizedDocument, props.notificationUrl());

			log.info("Issuing boleto: accountId={} payerDocument={} amount={} dueDate={}",
				accountId,
				normalizedDocument,
				request.amount(),
				request.dueDate());

			BoletoIssuanceResponse gatewayResponse = boletoGateway.issueBoleto(gatewayRequest);

			boleto.enrichWithProviderData(
				gatewayResponse.providerChargeId(),
				gatewayResponse.barcode(),
				gatewayResponse.billetLink(),
				gatewayResponse.pdfUrl()
			);

			Boleto saved = boletoRepository.save(boleto);

			IssueBoletoResponse response = IssueBoletoResponse.from(saved);
			idempotencyService.markProcessed(idempotencyKey, OPERATION_TYPE_ISSUE, response);

			log.info("Boleto issued: id={} providerChargeId={} accountId={} amount={}",
				saved.getId(),
				saved.getProviderChargeId(),
				accountId,
				saved.getAmount());

			metrics.recordBoletoIssued();

			return response;
		});
	}

	@Transactional
	public void processWebHookPayment (long providerChargeId, Instant receivedAt, String idempotencyKey) {
		if (isAlreadyProcessed(idempotencyKey, providerChargeId)) return;

		Boleto boleto = boletoRepository.findByProviderChargeId(providerChargeId)
			.orElseThrow(() -> {
				metrics.recordWebhookRejected("charge_not_found");
				log.warn("Webhook received for unknown providerChargeId={}", providerChargeId);
				return new BoletoNotFoundException(providerChargeId);
			});

		if (!boleto.getStatus().canTransitionTo(BoletoStatus.PAID)) {
			log.info("Boleto providerChargeId={} already in terminal status={}; webhook ignored",
				providerChargeId,
				boleto.getStatus());

			idempotencyService.markProcessed(idempotencyKey, OPERATION_TYPE_WEBHOOK, null);
		}

		String providerStatus = boletoGateway.getChargeStatus(providerChargeId);

		if (!PROVIDER_STATUS_PAID.equalsIgnoreCase(providerStatus)) {
			log.debug("Webhook for providerChargeId={} ignored; provider status is '{}'",
				providerChargeId,
				providerStatus);

			return;
		}

		Account account = accountRepository.findById(boleto.getAccountId())
			.orElseThrow(() -> new AccountNotFoundException(boleto.getAccountId()));

		Money creditAmount = Money.of(boleto.getAmount());

		boleto.markAsPaid(receivedAt);
		account.credit(creditAmount);

		var transaction = transactionRepository.save(
			Transaction.credit(
				account.getId(),
				creditAmount,
				"Boleto received; chargeId: " + providerChargeId,
				String.valueOf(providerChargeId)
			)
		);

		idempotencyService.markProcessed(idempotencyKey, OPERATION_TYPE_WEBHOOK, null);

		schedulePostCommitEvent(transaction);

		metrics.recordPaymentReceived();

		log.info("Boleto payment recorded: id={} providerChargeId={} accountId={} amount={} receivedAt={}",
			boleto.getId(),
			providerChargeId,
			boleto.getAccountId(),
			boleto.getAmount(),
			receivedAt);
	}

	private boolean isAlreadyProcessed (String idempotencyKey, long providerChargeId) {
		boolean duplicate = idempotencyService.findCachedResponse(idempotencyKey, Void.class).isPresent();

		if (duplicate) {
			log.debug("Webhook already processed (idempotent): providerChargeId={} key={}",
				providerChargeId, idempotencyKey);
		}

		return duplicate;
	}

	private void schedulePostCommitEvent (Transaction transaction) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit () {
				eventPublisher.publishEvent(
					TransactionCompletedEvent.ofSingleAccount(transaction));
			}
		});
	}

	private String normalizeDocument (String document) {
		return document.replaceAll("[^0-9]", "");
	}
}
