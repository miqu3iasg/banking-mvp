package com.miqu3iasg.banking.account.service;

import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.domain.AccountAction;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountTransactionalOperations {

	private final AccountRepository accountRepository;

	@Transactional
	Account execute (UUID accountId, AccountAction action) {
		Account account = accountRepository.findById(accountId)
			.orElseThrow(() -> new AccountNotFoundException(accountId));

		log.debug(
			"Executing {} on account {} [version={}, status={}]",
			action, account.getAccountNumber(), account.getVersion(), account.getStatus()
		);

		action.applyTo(account);

		Account saved = accountRepository.save(account);

		log.info(
			"Status transition complete [accountNumber={}, newStatus={}, action={}]",
			saved.getAccountNumber(), saved.getStatus(), action
		);

		return saved;
	}
}
