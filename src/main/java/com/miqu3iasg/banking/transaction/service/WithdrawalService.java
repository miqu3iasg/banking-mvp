package com.miqu3iasg.banking.transaction.service;

import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.shared.domain.Money;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import com.miqu3iasg.banking.shared.idempotency.IdempotentOperationExecutor;
import com.miqu3iasg.banking.transaction.api.dto.TransactionResponse;
import com.miqu3iasg.banking.transaction.api.dto.WithdrawalRequest;
import com.miqu3iasg.banking.transaction.domain.Transaction;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import com.miqu3iasg.banking.transaction.repository.TransactionRepository;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class WithdrawalService {
	private static final String OPERATION_TYPE = OperationType.WITHDRAWAL.name();

	private final AccountRepository accountRepository;
	private final TransactionRepository transactionRepository;
	private final TransactionMetrics metrics;
	private final TransactionEventPublisher transactionEventPublisher;
	private final IdempotentOperationExecutor idempotentOperationExecutor;
	private final RetryTemplate retryTemplate;
	private final TransactionTemplate transactionTemplate;

	public WithdrawalService (
		AccountRepository accountRepository,
		TransactionRepository transactionRepository,
		TransactionMetrics metrics,
		TransactionEventPublisher transactionEventPublisher,
		IdempotentOperationExecutor idempotentOperationExecutor,
		@Qualifier("withdrawalLockRetryTemplate") RetryTemplate retryTemplate,
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

	@Observed(name = "transaction.withdraw", contextualName = "WithdrawalService.withdraw")
	public TransactionResponse withdraw (String idempotencyKey, WithdrawalRequest request) {
		return metrics.timeWithdrawal(request.currency(), () ->
			idempotentOperationExecutor.execute(
				idempotencyKey,
				OPERATION_TYPE,
				TransactionResponse.class,
				() -> executeWithdrawal(idempotencyKey, request)
			));
	}

	private TransactionResponse executeWithdrawal (String idempotencyKey, WithdrawalRequest request) {
		return retryTemplate.execute(context -> {
			context.setAttribute("accountId", request.accountId().toString());

			return transactionTemplate.execute(status -> {
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

				transactionEventPublisher.schedulePostCommitEvent(saved);

				metrics.recordTransactionAmount(
					TransactionType.DEBIT,
					request.currency(),
					request.amount().doubleValue()
				);

				log.info("Withdrawal completed: account={} amount={} transactionId={}",
					account.getId(),
					amount,
					saved.getId());

				return TransactionResponse.from(saved);
			});
		});

	}
}
