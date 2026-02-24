package com.miqu3iasg.banking.account.service;

import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.domain.AccountAction;
import com.miqu3iasg.banking.account.domain.AccountStatus;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.account.service.AccountMetrics;
import com.miqu3iasg.banking.account.service.AccountService;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
class AccountStateTransitions {
	private final AccountRepository accountRepository;
	private final AccountMetrics metrics;

	@Transactional(propagation = Propagation.REQUIRED)
	public Account execute (UUID accountId, AccountAction action) {
		return metrics.timeTransitionDbWrite(action.name(), () -> {
			Account account = accountRepository.findByIdWithOptimisticLock(accountId)
				.orElseThrow(() -> new AccountNotFoundException(accountId));

			AccountStatus previousStatus = account.getStatus();

			log.debug(
				"Executing {} on account {} [currentStatus={}, version={}]",
				action,
				account.getAccountNumber(),
				previousStatus,
				account.getVersion()
			);

			action.apply(account);

			Account saved = accountRepository.save(account);

			log.info(
				"Status transition persisted [accountNumber={}, action={}, previousStatus={}, newStatus={}, version={}]",
				saved.getAccountNumber(), action,
				previousStatus, saved.getStatus(),
				saved.getVersion()
			);

			return saved;
		});
	}
}
