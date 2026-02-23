package com.miqu3iasg.banking.account.service;

import com.miqu3iasg.banking.account.api.dto.AccountResponse;
import com.miqu3iasg.banking.account.api.dto.CreateAccountRequest;
import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.domain.AccountAction;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.compliance.document.DocumentValidator;
import com.miqu3iasg.banking.shared.exception.*;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {
	private static final int LOCK_RETRY_MAX_ATTEMPTS = 3;
	private static final long LOCK_RETRY_BASE_DELAY_MS = 50L;
	private static final double LOCK_RETRY_MULTIPLIER = 2.0;

	private final AccountRepository accountRepository;
	private final AccountMetrics metrics;
	private final DocumentValidator documentValidator;
	private final AccountTransactionalOperations accountTransactionalOperations;

	@Transactional
	public AccountResponse openAccount (CreateAccountRequest request) {
		validateCreateAccountRequest(request);

		return metrics.timeAccountOpening(request.type(), () -> {
			log.info("Opening {} account for document ...{}", request.type(), request.documentNumber());

			if (accountRepository.existsByDocumentNumber(request.documentNumber())) {
				throw new AccountAlreadyExistsException(request.documentNumber());
			}

			String accountNumber = accountRepository.generateAccountNumber();
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
				saved.getAccountNumber(), saved.getType(), request.documentNumber()
			);

			return AccountResponse.from(saved);
		});
	}

	@Transactional(readOnly = true)
	public Account findById (UUID accountId) {
		return metrics.timeLookup(() ->
			accountRepository.findById(accountId)
				.orElseThrow(() -> {
					metrics.recordError(AccountFaultCode.ACCOUNT_NOT_FOUND.getCode());
					return new AccountNotFoundException(accountId);
				})
		);
	}

	/**
	 * Retries on optimistic-lock conflicts. The actual state mutation is delegated to
	 * {@link AccountTransactionalOperations#executeStatusAction}, which runs in its own
	 * {@code @Transactional} boundary — avoiding the self-proxy anti-pattern entirely.
	 */
	@Retryable(
		retryFor = {OptimisticLockException.class, OptimisticLockingFailureException.class},
		maxAttempts = LOCK_RETRY_MAX_ATTEMPTS,
		backoff = @Backoff(delay = LOCK_RETRY_BASE_DELAY_MS, multiplier = LOCK_RETRY_MULTIPLIER, random = true)
	)
	public AccountResponse applyStatusAction (UUID id, AccountAction action) {
		return metrics.timeStatusTransition(action.name(), () -> {
			log.info("Applying action {} to account {}", action, id);

			Account account = accountTransactionalOperations.executeStatusAction(id, action);

			return AccountResponse.from(account);
		});
	}

	private void validateCreateAccountRequest (CreateAccountRequest request) {
		requireNonBlank(request.holderName(), "holderName must not be blank", CustomerFaultCode.CUSTOMER_INVALID_INPUT);
		requireNonBlank(request.email(), "email must not be blank", CustomerFaultCode.CUSTOMER_INVALID_EMAIL);

		if (request.type() == null) {
			throw new InvalidRequestException("accountType must not be null", CustomerFaultCode.CUSTOMER_INVALID_INPUT);
		}

		documentValidator.validate(request.documentNumber());

		if (!isValidEmail(request.email())) {
			throw new InvalidRequestException(CustomerFaultCode.CUSTOMER_INVALID_EMAIL);
		}
	}

	/**
	 * Structural email check — not RFC 5322 compliant by design.
	 * Full validation is deferred to the delivery layer (e.g. sending a confirmation email).
	 */
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
