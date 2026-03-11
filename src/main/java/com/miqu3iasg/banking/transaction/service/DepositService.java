package com.miqu3iasg.banking.transaction.service;

import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.shared.domain.Money;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import com.miqu3iasg.banking.shared.idempotency.IdempotentOperationExecutor;
import com.miqu3iasg.banking.transaction.api.dto.DepositRequest;
import com.miqu3iasg.banking.transaction.api.dto.TransactionResponse;
import com.miqu3iasg.banking.transaction.domain.Transaction;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import com.miqu3iasg.banking.transaction.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class DepositService {
	private static final String OPERATION_DEPOSIT = OperationType.DEPOSIT.name();

	private final AccountRepository accountRepository;
	private final TransactionRepository transactionRepository;
	private final TransactionMetrics metrics;
	private final TransactionEventPublisher transactionEventPublisher;
	private final IdempotentOperationExecutor idempotentOperationExecutor;
	private final TransactionTemplate transactionTemplate;
	private final RetryTemplate retryTemplate;

	public DepositService (
		AccountRepository accountRepository,
		TransactionRepository transactionRepository,
		TransactionMetrics metrics,
		TransactionEventPublisher transactionEventPublisher,
		IdempotentOperationExecutor idempotentOperationExecutor,
		TransactionTemplate transactionTemplate,
		@Qualifier("depositLockRetryTemplate") RetryTemplate retryTemplate
	) {
		this.accountRepository = accountRepository;
		this.transactionRepository = transactionRepository;
		this.metrics = metrics;
		this.transactionEventPublisher = transactionEventPublisher;
		this.idempotentOperationExecutor = idempotentOperationExecutor;
		this.transactionTemplate = transactionTemplate;
		this.retryTemplate = retryTemplate;
	}

	public TransactionResponse deposit (String idempotencyKey, DepositRequest request) {
		return metrics.timeDeposit(request.currency(), () ->
			idempotentOperationExecutor.execute(
				idempotencyKey,
				OPERATION_DEPOSIT,
				TransactionResponse.class,
				() -> executeDeposit(idempotencyKey, request)
			)
		);
	}

	private TransactionResponse executeDeposit (String idempotencyKey, DepositRequest request) {
		return retryTemplate.execute(context -> {
			context.setAttribute("accountId", request.accountId().toString());

			return transactionTemplate.execute(status -> {
				Account account = accountRepository.findById(request.accountId())
					.orElseThrow(() -> new AccountNotFoundException(request.accountId()));

				Money amount = Money.of(request.amount(), request.currency());

				account.credit(amount);
				accountRepository.save(account);

				Transaction saved = transactionRepository.save(
					Transaction.credit(
						request.accountId(),
						amount,
						request.description(),
						idempotencyKey
					)
				);

				transactionEventPublisher.schedulePostCommitEvent(saved);

				metrics.recordTransactionAmount(
					TransactionType.CREDIT,
					request.currency(),
					request.amount().doubleValue()
				);

				log.info("Deposit completed: account=[{}] amount=[{}] transactionId=[{}]",
					account.getId(),
					amount,
					saved.getId());

				return TransactionResponse.from(saved);
			});
		});
	}
}
