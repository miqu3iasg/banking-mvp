package com.miqu3iasg.banking.transaction.service;

import com.miqu3iasg.banking.account.api.dto.TransactionResponse;
import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.shared.domain.Money;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import com.miqu3iasg.banking.shared.exception.SameAccountTransferException;
import com.miqu3iasg.banking.shared.idempotency.IdempotencyService;
import com.miqu3iasg.banking.transaction.api.dto.TransferRequest;
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

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {
	private static final String OPERATION_TYPE = OperationType.TRANSFER.name();

	private final AccountRepository accountRepository;
	private final TransactionRepository transactionRepository;
	private final IdempotencyService idempotencyService;
	private final TransactionMetrics metrics;
	private final ApplicationEventPublisher eventPublisher;

	public TransactionResponse transfer (String idempotencyKey, TransferRequest request) {
		if (request.originAccountId().equals(request.destinationAccountId())) {
			throw new SameAccountTransferException(request.originAccountId());
		}

		return metrics.timeTransfer(request.currency(), () -> executeTransfer(idempotencyKey, request));
	}

	@Transactional(rollbackFor = Exception.class)
	protected TransactionResponse executeTransfer (String idempotencyKey, TransferRequest request) {
		return idempotencyService
			.findCachedResponse(idempotencyKey, TransactionResponse.class)
			.map(cached -> {
				log.debug("Transfer idempotency HIT: key=[{}]", idempotencyKey);
				return cached;
			})
			.orElseGet(() -> performTransfer(idempotencyKey, request));
	}

	private TransactionResponse performTransfer (String idempotencyKey, TransferRequest request) {
		AccountPair accounts = loadAndLockAccounts(request.originAccountId(), request.destinationAccountId());

		Money amount = Money.of(request.amount(), request.currency());

		accounts.origin().debit(amount);
		accounts.destination().credit(amount);

		accountRepository.saveAll(List.of(accounts.origin(), accounts.destination()));

		// TODO: In a real-world scenario, we might want to handle the possibility of one transaction succeeding and the other failing, ensuring proper compensation or retry mechanisms.
		// TODO: Create a transfer method in Transaction entity to encapsulate the creation of both debit and credit transactions, ensuring they are linked by a common reference ID for easier tracking and reconciliation.
		List<Transaction> transactions = transactionRepository.saveAll(List.of(
			Transaction.debit(
				accounts.origin().getId(),
				amount,
				request.description(),
				idempotencyKey
			),

			Transaction.credit(
				accounts.destination().getId(),
				amount,
				request.description(),
				idempotencyKey
			)
		));

		Transaction debitTransaction = transactions.get(0);
		Transaction creditTransaction = transactions.get(1);

		schedulePostCommitEvents(debitTransaction, creditTransaction, accounts);

		log.info(
			"Transfer completed: origin={} destination={} amount={} debitTx={} creditTx={}",
			accounts.origin().getId(),
			accounts.destination().getId(),
			amount, debitTransaction.getId(),
			creditTransaction.getId()
		);

		TransactionResponse response = TransactionResponse.from(debitTransaction);

		idempotencyService.markProcessed(idempotencyKey, OPERATION_TYPE, response);

		metrics.recordTransactionAmount(
			TransactionType.TRANSFER_DEBIT,
			request.currency(),
			request.amount().doubleValue()
		);

		return response;
	}

	private AccountPair loadAndLockAccounts (UUID originId, UUID destinationId) {
		List<UUID> sorted = sortedIds(originId, destinationId);

		Account first = loadLockedAccount(sorted.get(0));
		Account second = loadLockedAccount(sorted.get(1));

		boolean firstIsOrigin = first.getId().equals(originId);

		return new AccountPair(firstIsOrigin ? first : second, firstIsOrigin ? second : first);
	}

	private Account loadLockedAccount (UUID id) {
		return accountRepository.findByIdWithLock(id)
			.orElseThrow(() -> new AccountNotFoundException(id));
	}

	private static List<UUID> sortedIds (UUID a, UUID b) {
		return a.compareTo(b) <= 0 ? List.of(a, b) : List.of(b, a);
	}

	/**
	 * Schedules post-commit events to be published after the current transaction
	 * successfully commits, ensuring listeners act only on persisted data.
	 *
	 * @param debitTransaction  the debit transaction created on the origin account
	 * @param creditTransaction the credit transaction created on the destination account
	 * @param accounts          the origin and destination account pair involved in the transfer
	 */
	private void schedulePostCommitEvents (
		Transaction debitTransaction,
		Transaction creditTransaction,
		AccountPair accounts
	) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit () {
				eventPublisher.publishEvent(new TransactionCompletedEvent(
					debitTransaction.getId(),
					accounts.origin().getId(),
					TransactionType.TRANSFER_DEBIT
				));

				eventPublisher.publishEvent(new TransactionCompletedEvent(
					creditTransaction.getId(),
					accounts.destination().getId(),
					TransactionType.TRANSFER_CREDIT
				));
			}
		});
	}

	private record AccountPair(Account origin, Account destination) { }
}
