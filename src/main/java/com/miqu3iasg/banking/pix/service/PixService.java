package com.miqu3iasg.banking.pix.service;

import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.pix.api.dto.CreatePixChargeRequest;
import com.miqu3iasg.banking.pix.config.EfiPixProperties;
import com.miqu3iasg.banking.pix.domain.PixCharge;
import com.miqu3iasg.banking.pix.domain.PixKey;
import com.miqu3iasg.banking.pix.domain.PixKeyStatus;
import com.miqu3iasg.banking.pix.gateway.*;
import com.miqu3iasg.banking.pix.metrics.PixMetrics;
import com.miqu3iasg.banking.pix.repository.PixChargeRepository;
import com.miqu3iasg.banking.pix.repository.PixKeyRepository;
import com.miqu3iasg.banking.shared.domain.Money;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import com.miqu3iasg.banking.pix.exception.PixChargeNotFoundException;
import com.miqu3iasg.banking.pix.exception.PixKeyNotFoundException;
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
public class PixService {
	private static final String OPERATION_TYPE_CHARGE = "PIX_CHARGE";
	private static final String OPERATION_TYPE_WEBHOOK = "PIX_WEBHOOK";

	private final PixChargeRepository chargeRepository;
	private final PixKeyRepository keyRepository;
	private final AccountRepository accountRepository;
	private final TransactionRepository transactionRepository;
	private final PixGateway pixGateway;
	private final IdempotencyService idempotencyService;
	private final EfiPixProperties pixProperties;
	private final PixMetrics metrics;
	private final ApplicationEventPublisher eventPublisher;

	public PixChargeResponse createCharge (
		UUID accountId,
		CreatePixChargeRequest request,
		String idempotencyKey
	) {
		return idempotencyService
			.findCachedResponse(idempotencyKey, PixChargeResponse.class)
			.map(cached -> {
				log.debug("Pix charge creation idempotency HIT: key=[{}]", idempotencyKey);

				return cached;
			})
			.orElseGet(() -> executeChargeCreation(accountId, request, idempotencyKey));
	}

	@Transactional
	private PixChargeResponse executeChargeCreation (
		UUID accountId,
		CreatePixChargeRequest request,
		String idempotencyKey
	) {
		return metrics.timeChargeCreation(() -> {
			String pixKey = keyRepository
				.findByAccountIdAndStatus(accountId, PixKeyStatus.ACTIVE)
				.stream()
				.findFirst()
				.map(PixKey::getValue)
				.orElseThrow(() -> new PixKeyNotFoundException("No active PIX key found for account: " + accountId));

			PixCharge charge = PixCharge.create(
				accountId,
				request.amount(),
				request.payerName(),
				request.payerCpfCnpj(),
				generateTxid(),
				pixProperties.chargeExpiresInSeconds()
			);

			chargeRepository.save(charge);

			PixChargeRequest gatewayRequest = PixChargeRequest
				.from(charge, pixKey, pixProperties.chargeExpiresInSeconds());

			PixChargeCreationResponse gatewayResult = pixGateway.createCharge(gatewayRequest);

			charge.enrichWithProviderData(gatewayResult.location(), gatewayResult.copyPaste());

			PixChargeResponse response = PixChargeResponse.from(charge);
			idempotencyService.markProcessed(idempotencyKey, OPERATION_TYPE_CHARGE, response);

			log.info("PIX charge created: txid={} accountId={} amount={}",
				charge.getTxid(),
				accountId,
				charge.getAmount());

			return response;
		});
	}

	@Transactional(readOnly = true)
	public PixChargeResponse getCharge (String txid, UUID accountId) {
		return chargeRepository.findByTxid(txid)
			.filter(c -> c.getAccountId().equals(accountId))
			.map(PixChargeResponse::from)
			.orElseThrow(() -> new PixChargeNotFoundException(txid));
	}

	@Transactional
	public void cancelCharge (String txid, UUID accountId) {
		var charge = chargeRepository.findByTxid(txid)
			.filter(c -> c.getAccountId().equals(accountId))
			.orElseThrow(() -> new PixChargeNotFoundException(txid));

		charge.cancel();

		pixGateway.cancelCharge(txid);

		log.info("PIX charge cancelled: txid={} accountId={}", txid, accountId);
	}

	@Transactional
	public void processWebhookPayment (String txid, Instant paidAt, String idempotencyKey) {
		if (isAlreadyProcessed(idempotencyKey, txid)) return;

		PixCharge charge = chargeRepository.findByTxid(txid)
			.orElseThrow(() -> {
				metrics.recordWebhookRejected("txid_not_found");

				return new PixChargeNotFoundException(txid);
			});

		Account account = accountRepository.findById(charge.getAccountId())
			.orElseThrow(() -> new AccountNotFoundException(charge.getAccountId()));

		Money creditAmount = Money.of(charge.getAmount());

		charge.markAsPaid(paidAt);
		account.credit(creditAmount);

		Transaction transaction = transactionRepository.save(
			Transaction.credit(
				account.getId(),
				creditAmount,
				"PIX received; txid: " + txid,
				txid
			)
		);

		idempotencyService.markProcessed(idempotencyKey, OPERATION_TYPE_WEBHOOK, null);

		schedulePostCommitEvent(transaction);

		metrics.recordPaymentReceived();

		log.info("PIX payment recorded: txid={} accountId={} amount={} paidAt={}",
			txid,
			charge.getAccountId(),
			charge.getAmount(),
			paidAt);
	}

	private boolean isAlreadyProcessed (String idempotencyKey, String txid) {
		boolean duplicate = idempotencyService.findCachedResponse(idempotencyKey, Void.class).isPresent();

		if (duplicate) {
			log.debug("Webhook already processed (idempotent): txid={} key={}", txid, idempotencyKey);
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

	private String generateTxid () {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 26).toUpperCase();
	}
}
