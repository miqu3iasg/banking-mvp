package com.miqu3iasg.banking.transaction.service;

import com.miqu3iasg.banking.account.api.dto.TransactionResponse;
import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.shared.domain.Money;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import com.miqu3iasg.banking.shared.exception.InvalidCurrencyException;
import com.miqu3iasg.banking.shared.idempotency.IdempotencyService;
import com.miqu3iasg.banking.transaction.api.dto.WithdrawalRequest;
import com.miqu3iasg.banking.transaction.domain.Transaction;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import com.miqu3iasg.banking.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Currency;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {
	private static final String OPERATION_TYPE = OperationType.WITHDRAWAL.name();

	private final AccountRepository accountRepository;
	private final TransactionRepository transactionRepository;
	private final IdempotencyService idempotencyService;
	private final TransactionMetrics metrics;
	private final ApplicationEventPublisher eventPublisher;

	public TransactionResponse withdraw (String idempotencyKey, WithdrawalRequest request) {
		return metrics.timeWithdrawal(request.currency(), () -> executeWithdrawal(idempotencyKey, request));
	}

	@Transactional(rollbackFor = Exception.class)
	protected TransactionResponse executeWithdrawal (String idempotencyKey, WithdrawalRequest request) {
		return idempotencyService
			.findCachedResponse(idempotencyKey, TransactionResponse.class)
			.map(cached -> {
				log.debug("Withdrawal idempotency HIT: key=[{}]", idempotencyKey);
				return cached;
			})
			.orElseGet(() -> performWithdrawal(idempotencyKey, request));
	}

	private TransactionResponse performWithdrawal (String idempotencyKey, WithdrawalRequest request) {
		Account account = accountRepository.findById(request.accountId())
			.orElseThrow(() -> new AccountNotFoundException(request.accountId()));

		Money amount = Money.of(request.amount(), request.currency());

		account.debit(amount);
		accountRepository.save(account);

		Transaction saved = transactionRepository.save(
			Transaction.debit(
				request.accountId(),
				amount,
				request.description(),
				idempotencyKey
			)
		);

		schedulePostCommitEvent(saved, account);

		log.info(
			"Withdrawal completed: account={} amount={} transactionId={}",
			account.getId(), amount, saved.getId()
		);

		TransactionResponse response = TransactionResponse.from(saved);

		idempotencyService.markProcessed(idempotencyKey, OPERATION_TYPE, response);

		metrics.recordTransactionAmount(
			TransactionType.DEBIT,
			request.currency(),
			request.amount().doubleValue()
		);

		return response;
	}

	/**
	 * Schedules a post-commit event to be published after the current transaction
	 * successfully commits, ensuring listeners act only on persisted data.
	 *
	 * @param transaction the completed transaction to be published as an event
	 * @param account     the account associated with the withdrawal
	 */
	private void schedulePostCommitEvent (Transaction transaction, Account account) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit () {
				eventPublisher.publishEvent(new TransactionCompletedEvent(
					transaction.getId(),
					account.getId(),
					TransactionType.DEBIT
				));
			}
		});
	}
}
