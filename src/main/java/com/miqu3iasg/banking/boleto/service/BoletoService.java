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
import com.miqu3iasg.banking.shared.idempotency.IdempotentOperationExecutor;
import com.miqu3iasg.banking.transaction.domain.Transaction;
import com.miqu3iasg.banking.transaction.repository.TransactionRepository;
import com.miqu3iasg.banking.transaction.service.TransactionEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@ConditionalOnProperty(name = "efi.webclient.enabled", havingValue = "true", matchIfMissing = true)
@Service
@RequiredArgsConstructor
public class BoletoService {

	private static final String OPERATION_TYPE_ISSUE = "BOLETO_ISSUE";
	private static final String WEBHOOK_PAYMENT = "BOLETO_WEBHOOK";
	private static final String PROVIDER_STATUS_PAID = "paid";

	private final BoletoRepository boletoRepository;
	private final AccountRepository accountRepository;
	private final TransactionRepository transactionRepository;
	private final BoletoGateway boletoGateway;
	private final IdempotentOperationExecutor idempotentOperationExecutor;
	private final TransactionEventPublisher transactionEventPublisher;
	private final TransactionTemplate transactionTemplate;
	private final EfiBoletoProperties props;
	private final BoletoMetrics metrics;

	public IssueBoletoResponse issue (UUID accountId, IssueBoletoRequest request, String idempotencyKey) {
		return idempotentOperationExecutor.execute(
			idempotencyKey,
			OPERATION_TYPE_ISSUE,
			IssueBoletoResponse.class,
			() -> issueWithin(accountId, request)
		);
	}

	public void processWebhookPayment (long providerChargeId, Instant receivedAt, String idempotencyKey) {
		idempotentOperationExecutor.execute(
			idempotencyKey,
			WEBHOOK_PAYMENT,
			Void.class,
			() -> {
				paymentWebhookWithin(providerChargeId, receivedAt);
				return null;
			}
		);
	}

	private IssueBoletoResponse issueWithin (UUID accountId, IssueBoletoRequest request) {
		return transactionTemplate.execute(status -> {
			accountRepository.findById(accountId)
				.orElseThrow(() -> new AccountNotFoundException(accountId));

			String normalizedDocument = normalizeDocument(request.payerDocument());

			Boleto boleto = Boleto.issue(
				accountId,
				request.payerName(),
				normalizedDocument,
				request.address().toDomain(),
				request.amount(),
				request.dueDate(),
				request.description()
			);

			BoletoIssuanceRequest gatewayRequest = BoletoIssuanceRequest
				.from(boleto, normalizedDocument, props.notificationUrl());

			BoletoIssuanceResponse gatewayResponse = metrics
				.timeBoletoIssuance(() -> boletoGateway.issue(gatewayRequest));

			boleto.enrichWithProviderData(
				gatewayResponse.providerChargeId(),
				gatewayResponse.barcode(),
				gatewayResponse.billetLink(),
				gatewayResponse.pdfUrl()
			);

			boleto = boletoRepository.save(boleto);

			metrics.recordBoletoIssued();

			log.info("Boleto issued: accountId=[{}] id=[{}] providerChargeId=[{}] amount=[{}]",
				accountId,
				boleto.getId(),
				boleto.getProviderChargeId(),
				boleto.getAmount());

			return IssueBoletoResponse.from(boleto);
		});
	}

	private void paymentWebhookWithin (long providerChargeId, Instant receivedAt) {
		transactionTemplate.execute(status -> {
			Boleto boleto = boletoRepository.findByProviderChargeId(providerChargeId)
				.orElseThrow(() -> unrecognizedCharge(providerChargeId));

			if (!boleto.getStatus().canTransitionTo(BoletoStatus.PAID)) {
				log.info("Boleto providerChargeId=[{}] already in terminal status=[{}]; webhook ignored",
					providerChargeId,
					boleto.getStatus());

				return null;
			}

			String providerStatus = boletoGateway.getChargeStatus(providerChargeId);

			if (!PROVIDER_STATUS_PAID.equalsIgnoreCase(providerStatus)) {
				log.debug("Webhook for providerChargeId=[{}] ignored; provider status is '{}'",
					providerChargeId,
					providerStatus);

				return null;
			}

			Account account = accountRepository.findById(boleto.getAccountId())
				.orElseThrow(() -> new AccountNotFoundException(boleto.getAccountId()));

			applyPayment(boleto, account, receivedAt, providerChargeId);

			return null;
		});
	}

	private void applyPayment (Boleto boleto, Account account, Instant receivedAt, long providerChargeId) {
		boleto.markAsPaid(receivedAt);
		boletoRepository.save(boleto);

		Money amount = Money.of(boleto.getAmount());
		account.credit(amount);
		accountRepository.save(account);

		Transaction transaction = Transaction.credit(
			account.getId(),
			amount,
			"Boleto received; chargeId: " + providerChargeId,
			Long.toString(providerChargeId) // This is the idempotency key
		);

		transactionRepository.save(transaction);

		transactionEventPublisher.schedulePostCommitEvent(transaction);

		metrics.recordPaymentReceived();

		log.info("Boleto payment recorded: accountId=[{}] id=[{}] providerChargeId=[{}] amount=[{}] receivedAt=[{}]",
			boleto.getAccountId(),
			boleto.getId(),
			providerChargeId,
			boleto.getAmount(),
			receivedAt);
	}

	private BoletoNotFoundException unrecognizedCharge (long providerChargeId) {
		metrics.recordWebhookRejected("charge_not_found");
		log.warn("Webhook received for unknown providerChargeId=[{}]", providerChargeId);
		return new BoletoNotFoundException(providerChargeId);
	}

	private String normalizeDocument (String document) {
		return document.replaceAll("[^0-9]", "");
	}

	public Boleto findById (UUID boletoId) {
		return boletoRepository.findById(boletoId)
			.orElseThrow(() -> new BoletoNotFoundException(boletoId));
	}
}
