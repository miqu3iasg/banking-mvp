package com.miqu3iasg.banking.pix.service;

import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.pix.api.dto.CreatePixChargeRequest;
import com.miqu3iasg.banking.pix.config.EfiPixProperties;
import com.miqu3iasg.banking.pix.domain.PixCharge;
import com.miqu3iasg.banking.pix.domain.PixKey;
import com.miqu3iasg.banking.pix.domain.PixKeyStatus;
import com.miqu3iasg.banking.pix.exception.PixChargeNotFoundException;
import com.miqu3iasg.banking.pix.exception.PixKeyNotFoundException;
import com.miqu3iasg.banking.pix.gateway.PixChargeCreationResponse;
import com.miqu3iasg.banking.pix.gateway.PixChargeRequest;
import com.miqu3iasg.banking.pix.gateway.PixChargeResponse;
import com.miqu3iasg.banking.pix.gateway.PixGateway;
import com.miqu3iasg.banking.pix.metrics.PixMetrics;
import com.miqu3iasg.banking.pix.repository.PixChargeRepository;
import com.miqu3iasg.banking.pix.repository.PixKeyRepository;
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
public class PixService {
	private static final String OPERATION_CREATE_CHARGE = "PIX_CREATE_CHARGE";
	private static final String OPERATION_WEBHOOK_PAYMENT = "PIX_WEBHOOK_PAYMENT";

	private final PixChargeRepository chargeRepository;
	private final PixKeyRepository keyRepository;
	private final AccountRepository accountRepository;
	private final TransactionRepository transactionRepository;
	private final PixGateway pixGateway;
	private final IdempotencyService idempotencyService;
	private final PixMetrics metrics;
	private final EfiPixProperties efiPixProperties;
	private final ApplicationEventPublisher eventPublisher;

	public PixChargeResponse createCharge (
		UUID accountId,
		CreatePixChargeRequest request,
		String idempotencyKey
	) {
		var cached = idempotencyService.findCachedResponse(idempotencyKey, PixChargeResponse.class);

		if (cached.isPresent()) {
			log.debug("createCharge idempotency HIT: key=[{}]", idempotencyKey);
			return cached.get();
		}

		boolean winner = idempotencyService.claimKey(idempotencyKey, OPERATION_CREATE_CHARGE);
		if (!winner) {
			log.debug("createCharge idempotency LOSER: key=[{}]; waiting for winner to complete", idempotencyKey);

			return idempotencyService
				.awaitCompletedResponse(idempotencyKey, PixChargeResponse.class)
				.orElseGet(() -> executeCreateCharge(accountId, request, idempotencyKey));
		}

		try {
			PixChargeResponse response = executeCreateCharge(accountId, request, idempotencyKey);

			idempotencyService.completeKey(idempotencyKey, OPERATION_CREATE_CHARGE, response);

			return response;
		} catch (Exception e) {
			idempotencyService.deletePendingKey(idempotencyKey);
			throw e;
		}
	}

	@Transactional
	protected PixChargeResponse executeCreateCharge (
		UUID accountId,
		CreatePixChargeRequest request,
		String idempotencyKey
	) {
		String activeKey = keyRepository
			.findByAccountIdAndStatus(accountId, PixKeyStatus.ACTIVE)
			.stream()
			.findFirst()
			.map(PixKey::getValue)
			.orElseThrow(() -> new PixKeyNotFoundException(
				"No active PIX key found for accountId=[%s]".formatted(accountId)
			));

		String txid = generateTxid();

		PixCharge charge = PixCharge.create(
			accountId,
			request.amount(),
			request.payerName(),
			request.payerCpfCnpj(),
			txid,
			efiPixProperties.chargeExpiresInSeconds()
		);

		PixChargeRequest gatewayRequest = PixChargeRequest
			.from(charge, activeKey, efiPixProperties.chargeExpiresInSeconds());

		PixChargeCreationResponse gatewayResponse = metrics
			.timeChargeCreation(() -> pixGateway.createCharge(gatewayRequest));

		charge.enrichWithProviderData(
			gatewayResponse.txid(),
			gatewayResponse.location(),
			gatewayResponse.copyPaste()
		);

		chargeRepository.save(charge);

		log.info("PIX charge created: txid=[{}] account=[{}] amount=[{}]",
			txid,
			accountId,
			request.amount());

		return PixChargeResponse.from(charge);
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
		var cached = idempotencyService.findCachedResponse(idempotencyKey, PixChargeResponse.class);

		if (cached.isPresent()) {
			log.debug("processWebhookPayment idempotency HIT: key=[{}]", idempotencyKey);
			return;
		}

		boolean winner = idempotencyService.claimKey(idempotencyKey, OPERATION_WEBHOOK_PAYMENT);
		if (!winner) {
			idempotencyService.awaitCompletedResponse(idempotencyKey, PixChargeResponse.class);

			return;
		}

		try {
			PixChargeResponse response = executeWebhookPayment(txid, paidAt);
			idempotencyService.completeKey(idempotencyKey, OPERATION_WEBHOOK_PAYMENT, response);
		} catch (Exception e) {
			idempotencyService.deletePendingKey(idempotencyKey);
			throw e;
		}
	}

	@Transactional
	protected PixChargeResponse executeWebhookPayment(String txid, Instant paidAt) {
		PixCharge charge = chargeRepository.findByTxid(txid)
			.orElseThrow(() -> new PixChargeNotFoundException(txid));

		Account account = accountRepository.findById(charge.getAccountId())
			.orElseThrow(() -> new AccountNotFoundException(charge.getAccountId()));

		charge.markAsPaid(paidAt);
		chargeRepository.save(charge);

		account.credit(Money.of(charge.getAmount()));
		accountRepository.save(account);

		Transaction transaction = Transaction.credit(
			account.getId(),
			Money.brl(charge.getAmount()),
			"PIX received; txid: " + txid,
			txid
		);

		transactionRepository.save(transaction);

		schedulePostCommitEvent(transaction);

		metrics.recordPaymentReceived();

		log.info("PIX payment processed: txid=[{}] account=[{}] amount=[{}]",
			txid,
			account.getId(),
			charge.getAmount());

		return PixChargeResponse.from(charge);
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
