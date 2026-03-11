package com.miqu3iasg.banking.account.service;

import com.miqu3iasg.banking.account.api.dto.AccountResponse;
import com.miqu3iasg.banking.account.api.dto.CreateAccountRequest;
import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.domain.AccountAction;
import com.miqu3iasg.banking.account.exception.AccountAlreadyExistsException;
import com.miqu3iasg.banking.account.exception.AccountFaultCode;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.compliance.document.DocumentValidator;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import com.miqu3iasg.banking.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class AccountService {

	/**
	 * Maximum retry attempts for optimistic-lock conflicts.
	 * Attempt 1 is the initial try; attempts 2–N are actual retries.
	 */
	private static final int LOCK_RETRY_MAX_ATTEMPTS = 3;

	private final AccountRepository accountRepository;
	private final AccountMetrics metrics;
	private final DocumentValidator documentValidator;
	private final AccountStateTransitions accountStateTransitions;
	private final AccountNumberGenerator accountNumberGenerator;
	private final RetryTemplate retryTemplate;

	public AccountService (
		AccountRepository accountRepository,
		AccountMetrics metrics,
		DocumentValidator documentValidator,
		AccountStateTransitions accountStateTransitions,
		AccountNumberGenerator accountNumberGenerator,
		@Qualifier("accountLockRetryTemplate") RetryTemplate retryTemplate
	) {
		this.accountRepository = accountRepository;
		this.metrics = metrics;
		this.documentValidator = documentValidator;
		this.accountStateTransitions = accountStateTransitions;
		this.accountNumberGenerator = accountNumberGenerator;
		this.retryTemplate = retryTemplate;
	}


	@Transactional(isolation = Isolation.READ_COMMITTED)
	public AccountResponse openAccount (CreateAccountRequest request) {
		return metrics.timeAccountOpening(request.type(), () -> {
			documentValidator.validate(request.documentNumber());

			log.info("Opening {} account for document ...{}", request.type(), request.documentNumber());

			if (accountRepository.existsByDocumentNumber(request.documentNumber())) {
				log.warn("Account opening rejected; document already registered [document={}]", request.documentNumber());
				throw new AccountAlreadyExistsException(request.documentNumber());
			}

			Account saved = provisionAccount(request);

			log.info("Account opened [accountNumber={}, type={}, document=...{}]",
				saved.getAccountNumber(),
				saved.getType(),
				request.documentNumber());

			return AccountResponse.from(saved);
		});
	}


	private Account provisionAccount (CreateAccountRequest request) {
		Account account = Account.open(
			accountNumberGenerator.generate(),
			request.type(),
			request.holderName(),
			request.documentNumber(),
			request.email()
		);

		try {
			return accountRepository.save(account);
		} catch (DataIntegrityViolationException ex) {
			log.warn("Duplicate account detected at persistence layer [document=...{}]", request.documentNumber());

			throw new AccountAlreadyExistsException(request.documentNumber());
		}
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

			Account account = retryTemplate.execute(
				context -> {
					context.setAttribute("action", action.name());
					context.setAttribute("accountId", id.toString());

					return accountStateTransitions.execute(id, action);
				},

				context -> {
					Throwable lastThrowable = context.getLastThrowable();

					log.error("Exhausted {} retry attempts for action {} on account {}",
						LOCK_RETRY_MAX_ATTEMPTS,
						action,
						id,
						lastThrowable);

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
}
