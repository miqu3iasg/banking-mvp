package com.miqu3iasg.banking.transaction.service;

import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.shared.domain.Money;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import com.miqu3iasg.banking.shared.idempotency.IdempotentOperationExecutor;
import com.miqu3iasg.banking.transaction.api.dto.TransactionResponse;
import com.miqu3iasg.banking.transaction.api.dto.TransferRequest;
import com.miqu3iasg.banking.transaction.domain.Transaction;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import com.miqu3iasg.banking.transaction.repository.TransactionRepository;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class TransferService {
	private static final String OPERATION_TRANSFER = OperationType.TRANSFER.name();

	private final AccountRepository accountRepository;
	private final TransactionRepository transactionRepository;
	private final TransactionMetrics metrics;
	private final TransactionEventPublisher transactionEventPublisher;
	private final IdempotentOperationExecutor idempotentOperationExecutor;
	private final RetryTemplate retryTemplate;
	private final TransactionTemplate transactionTemplate;

	public TransferService (
		AccountRepository accountRepository,
		TransactionRepository transactionRepository,
		TransactionMetrics metrics,
		TransactionEventPublisher transactionEventPublisher,
		IdempotentOperationExecutor idempotentOperationExecutor,
		@Qualifier("transferLockRetryTemplate") RetryTemplate retryTemplate,
		TransactionTemplate transactionTemplate
	) {
		this.accountRepository = accountRepository;
		this.transactionRepository = transactionRepository;
		this.metrics = metrics;
		this.transactionEventPublisher = transactionEventPublisher;
		this.idempotentOperationExecutor = idempotentOperationExecutor;
		this.retryTemplate = retryTemplate;
		this.transactionTemplate = transactionTemplate;
	}

	@Observed(name = "transaction.transfer", contextualName = "TransferService.transfer")
	public TransactionResponse transfer (String idempotencyKey, TransferRequest request) {
		return metrics.timeTransfer(request.currency(), () ->
			idempotentOperationExecutor.execute(
				idempotencyKey,
				OPERATION_TRANSFER,
				TransactionResponse.class,
				() -> executeTransfer(idempotencyKey, request)
			)
		);
	}

	private TransactionResponse executeTransfer (String idempotencyKey, TransferRequest request) {
		return retryTemplate.execute(context -> {
			context.setAttribute("originAccountId", request.originAccountId().toString());
			context.setAttribute("destinationAccountId", request.destinationAccountId().toString());

			return transactionTemplate.execute(status -> {
				TransactionEventPublisher.AccountPair accounts =
					loadAndLockAccounts(request.originAccountId(), request.destinationAccountId());

				Money amount = Money.of(request.amount(), request.currency());

				accounts.origin().debit(amount);
				accounts.destination().credit(amount);

				accountRepository.saveAll(List.of(accounts.origin(), accounts.destination()));

				List<Transaction> transactions = transactionRepository.saveAll(
					Transaction.transfer(
						accounts.origin().getId(),
						accounts.destination().getId(),
						amount,
						request.description(),
						idempotencyKey
					)
				);

				Transaction debitTransaction = transactions.get(0);
				Transaction creditTransaction = transactions.get(1);

				transactionEventPublisher.schedulePostCommitEvents(debitTransaction, creditTransaction, accounts);

				log.info("Transfer completed: origin={} destination={} amount={} referenceId={} debitTx={} creditTx={}",
					accounts.origin().getId(),
					accounts.destination().getId(),
					amount,
					debitTransaction.getReferenceId(),
					debitTransaction.getId(),
					creditTransaction.getId());

				metrics.recordTransactionAmount(
					TransactionType.TRANSFER_DEBIT,
					request.currency(),
					request.amount().doubleValue()
				);

				return TransactionResponse.from(debitTransaction);
			});
		});
	}

	private TransactionEventPublisher.AccountPair loadAndLockAccounts (UUID originId, UUID destinationId) {
		List<UUID> sorted = sortedIds(originId, destinationId);

		Account first = loadLockedAccount(sorted.get(0));
		Account second = loadLockedAccount(sorted.get(1));

		boolean firstIsOrigin = first.getId().equals(originId);

		return new TransactionEventPublisher
			.AccountPair(firstIsOrigin ? first : second, firstIsOrigin ? second : first);
	}

	private Account loadLockedAccount (UUID id) {
		return accountRepository.findByIdWithPessimisticLock(id)
			.orElseThrow(() -> new AccountNotFoundException(id));
	}

	private static List<UUID> sortedIds (UUID a, UUID b) {
		return a.compareTo(b) <= 0 ? List.of(a, b) : List.of(b, a);
	}
}
