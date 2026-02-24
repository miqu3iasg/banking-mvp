package com.miqu3iasg.banking.account.service;

import com.miqu3iasg.banking.account.api.dto.AccountResponse;
import com.miqu3iasg.banking.account.api.dto.CreateAccountRequest;
import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.domain.AccountAction;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.compliance.document.DocumentValidator;
import com.miqu3iasg.banking.shared.exception.*;
import com.miqu3iasg.banking.shared.exception.code.AccountFaultCode;
import com.miqu3iasg.banking.shared.exception.code.CustomerFaultCode;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

// TODO: publish domain events on account status changes (e.g. for notification or audit purposes)
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

	/**
	 * Maximum retry attempts for optimistic-lock conflicts.
	 * Attempt 1 is the initial try; attempts 2–N are actual retries.
	 */
	private static final int LOCK_RETRY_MAX_ATTEMPTS = 3;

	/**
	 * Base delay in milliseconds before the first retry.
	 */
	private static final long LOCK_RETRY_BASE_DELAY_MS = 50L;

	/**
	 * Exponential multiplier applied to the base delay on each subsequent retry.
	 * With base=50ms and multiplier=2.0: delays are ~50ms, ~100ms (plus random jitter).
	 */
	private static final double LOCK_RETRY_MULTIPLIER = 2.0;

	private final AccountRepository accountRepository;
	private final AccountMetrics metrics;
	private final DocumentValidator documentValidator;
	private final AccountStateTransitions accountStateTransitions;
	private final AccountNumberGenerator accountNumberGenerator; // TODO 4

	private RetryTemplate buildAccountRetryTemplate () {
		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
			LOCK_RETRY_MAX_ATTEMPTS,
			Map.of(
				OptimisticLockException.class, true,
				OptimisticLockingFailureException.class, true,
				TransientDataAccessException.class, true
			),
			/* traverseCauses= */ true,
			/* defaultValue (non-listed exceptions not retried) = */ false
		);

		ExponentialRandomBackOffPolicy backOff = new ExponentialRandomBackOffPolicy();

		backOff.setInitialInterval(LOCK_RETRY_BASE_DELAY_MS);
		backOff.setMultiplier(LOCK_RETRY_MULTIPLIER);
		backOff.setMaxInterval(LOCK_RETRY_BASE_DELAY_MS * 10); // cap at ~500ms

		RetryTemplate template = new RetryTemplate();

		template.setRetryPolicy(retryPolicy);
		template.setBackOffPolicy(backOff);

		template.registerListener(accountRetryListener());

		return template;
	}

	private RetryListener accountRetryListener () {
		return new RetryListener() {
			@Override
			public <T, E extends Throwable> void onError (
				RetryContext context, RetryCallback<T, E> callback, Throwable t) {
				int attempt = context.getRetryCount(); // 0-indexed; 0 = first failure
				String action = (String) context.getAttribute("action");
				String accountId = (String) context.getAttribute("accountId");

				if (TransientExceptionClassifier.isRetryable(t)) {
					log.warn(
						"Optimistic-lock conflict on account {} action {} (attempt {}/{}), retrying…",
						accountId,
						action,
						attempt + 1,
						LOCK_RETRY_MAX_ATTEMPTS,
						t
					);

					metrics.recordLockRetry(action != null ? action : "-", attempt + 1);
				} else {
					log.error(
						"Non-retryable failure on account {} action {} (attempt {})",
						accountId,
						action,
						attempt + 1,
						t
					);
				}
			}
		};
	}

	@Transactional
	public AccountResponse openAccount (CreateAccountRequest request) {
		validateCreateAccountRequest(request);

		return metrics.timeAccountOpening(request.type(), () -> {
			log.info("Opening {} account for document ...{}", request.type(), request.documentNumber());

			if (accountRepository.existsByDocumentNumber(request.documentNumber())) {
				throw new AccountAlreadyExistsException(request.documentNumber());
			}

			String accountNumber = accountNumberGenerator.generate();

			Account account = Account.open(
				accountNumber,
				request.type(),
				request.holderName(),
				request.documentNumber(),
				request.email()
			);

			Account saved = accountRepository.save(account);

			log.info(
				"Account opened [accountNumber={}, type={}, document=...{}]",
				saved.getAccountNumber(),
				saved.getType(),
				request.documentNumber()
			);

			return AccountResponse.from(saved);
		});
	}

	@Transactional(readOnly = true)
	public AccountResponse findById (UUID accountId) {
		return metrics.timeLookup(() ->
			accountRepository.findById(accountId)
				.map(AccountResponse::from)
				.orElseThrow(() -> {
					metrics.recordError(AccountFaultCode.ACCOUNT_NOT_FOUND.getCode());
					return new AccountNotFoundException(accountId);
				})
		);
	}

	public AccountResponse applyStatusAction (UUID id, AccountAction action) {
		return metrics.timeStatusTransition(action.name(), () -> {
			log.info("Applying action {} to account {}", action, id);

			RetryTemplate retryTemplate = buildAccountRetryTemplate();

			Account account = retryTemplate.execute(
				context -> {
					context.setAttribute("action", action.name());
					context.setAttribute("accountId", id.toString());

					return accountStateTransitions.execute(id, action);
				},

				context -> {
					Throwable lastThrowable = context.getLastThrowable();

					log.error(
						"Exhausted {} retry attempts for action {} on account {}",
						LOCK_RETRY_MAX_ATTEMPTS,
						action,
						id,
						lastThrowable
					);

					metrics.recordError(
						lastThrowable instanceof BusinessException be
							? be.getErrorCode()
							: "lock_retry_exhausted"
					);

					if (lastThrowable instanceof RuntimeException re) throw re;

					throw new RuntimeException(lastThrowable);
				},
				new AccountIdRetryState(id)
			);

			return AccountResponse.from(account);
		});
	}

	private void validateCreateAccountRequest (CreateAccountRequest request) {
		requireNonBlank(request.holderName(), "holderName must not be blank", CustomerFaultCode.CUSTOMER_INVALID_INPUT);
		requireNonBlank(request.email(), "email must not be blank", CustomerFaultCode.CUSTOMER_INVALID_EMAIL);

		if (request.type() == null) {
			throw new InvalidRequestException(
				"accountType must not be null",
				CustomerFaultCode.CUSTOMER_INVALID_INPUT
			);
		}

		documentValidator.validate(request.documentNumber());

		if (!isValidEmail(request.email())) {
			throw new InvalidRequestException(CustomerFaultCode.CUSTOMER_INVALID_EMAIL);
		}
	}

	private static boolean isValidEmail (String email) {
		int atIndex = email.indexOf('@');
		return atIndex > 0 && email.lastIndexOf('.') > atIndex;
	}

	private static void requireNonBlank (String value, String message, CustomerFaultCode faultCode) {
		if (value == null || value.isBlank()) {
			throw new InvalidRequestException(message, faultCode);
		}
	}
}
