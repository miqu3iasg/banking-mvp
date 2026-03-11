package com.miqu3iasg.banking.transaction.service;

import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.shared.config.RetryProperties;
import com.miqu3iasg.banking.shared.domain.Money;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import com.miqu3iasg.banking.shared.exception.IdempotencyTimeoutException;
import com.miqu3iasg.banking.shared.exception.TransientExceptionClassifier;
import com.miqu3iasg.banking.shared.idempotency.IdempotencyService;
import com.miqu3iasg.banking.transaction.api.dto.DepositRequest;
import com.miqu3iasg.banking.transaction.api.dto.TransactionResponse;
import com.miqu3iasg.banking.transaction.domain.Transaction;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import com.miqu3iasg.banking.transaction.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Slf4j
@Service
public class DepositService {
	private static final String OPERATION_DEPOSIT = OperationType.DEPOSIT.name();

	private final AccountRepository accountRepository;
	private final TransactionRepository transactionRepository;
	private final IdempotencyService idempotencyService;
	private final TransactionMetrics metrics;
	private final ApplicationEventPublisher eventPublisher;
	private final TransactionTemplate transactionTemplate;
	private final RetryTemplate retryTemplate;
	private final RetryProperties props;

	public DepositService (
		AccountRepository accountRepository,
		TransactionRepository transactionRepository,
		IdempotencyService idempotencyService,
		TransactionMetrics metrics,
		ApplicationEventPublisher eventPublisher,
		TransactionTemplate transactionTemplate,
		@Qualifier("depositLockRetryTemplate") RetryTemplate retryTemplate,
		RetryProperties props
	) {
		this.accountRepository = accountRepository;
		this.transactionRepository = transactionRepository;
		this.idempotencyService = idempotencyService;
		this.metrics = metrics;
		this.eventPublisher = eventPublisher;
		this.transactionTemplate = transactionTemplate;
		this.retryTemplate = retryTemplate;
		this.props = props;
		this.retryTemplate.registerListener(depositRetryListener());
	}

	public TransactionResponse deposit (String idempotencyKey, DepositRequest request) {
		return metrics.timeDeposit(request.currency(), () ->
			executeOrAwaitIdempotentOperation(
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

				schedulePostCommitEvent(saved);

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

	private <T> T executeOrAwaitIdempotentOperation (
		String idempotencyKey,
		String operation,
		Class<T> responseType,
		Supplier<T> action
	) {
		var cached = idempotencyService.findCachedResponse(idempotencyKey, responseType);
		if (cached.isPresent()) {
			log.debug("idempotency HIT: operation=[{}] key=[{}]", operation, idempotencyKey);
			return cached.get();
		}

		boolean winner = idempotencyService.claimKey(idempotencyKey, operation);
		if (!winner) {
			log.debug("idempotency LOSER: operation=[{}] key=[{}]; awaiting winner", operation, idempotencyKey);
			return idempotencyService
				.awaitCompletedResponse(idempotencyKey, responseType)
				.orElseThrow(() -> new IdempotencyTimeoutException(idempotencyKey));
		}

		try {
			T response = action.get();
			idempotencyService.completeKey(idempotencyKey, operation, response);
			return response;
		} catch (Exception e) {
			idempotencyService.deletePendingKey(idempotencyKey);
			throw e;
		}
	}

	private void schedulePostCommitEvent (Transaction transaction) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit () {
				eventPublisher.publishEvent(
					TransactionCompletedEvent.ofSingleAccount(transaction)
				);
			}
		});
	}

	private RetryListener depositRetryListener () {
		return new RetryListener() {
			@Override
			public <T, E extends Throwable> void onError (
				RetryContext context, RetryCallback<T, E> callback, Throwable t) {
				int attempt = context.getRetryCount();
				String action = (String) context.getAttribute("action");
				String accountId = (String) context.getAttribute("accountId");

				if (TransientExceptionClassifier.isRetryable(t)) {
					log.warn(
						"Optimistic-lock conflict on deposit accountId={} (attempt {}/{}), retrying…",
						accountId,
						attempt + 1,
						props.maxAttempts(),
						t
					);

					metrics.recordLockRetry(action != null ? action : "-", attempt + 1);
				} else {
					log.error("Non-retryable failure on deposit {} action {} (attempt {})",
						accountId,
						action,
						attempt + 1,
						t);
				}
			}
		};
	}
}
