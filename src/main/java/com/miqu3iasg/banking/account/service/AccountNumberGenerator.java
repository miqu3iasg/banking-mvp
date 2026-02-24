package com.miqu3iasg.banking.account.service;

import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.shared.exception.AccountNumberGenerationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountNumberGenerator {
	private static final int MAX_COLLISION_RETRIES = 3;

	private final AccountRepository accountRepository;

	public String generate () {
		for (int attempt = 1; attempt <= MAX_COLLISION_RETRIES; attempt++) {
			String candidate = accountRepository.generateAccountNumber();

			if (!accountRepository.existsByAccountNumber(candidate)) {
				log.debug(
					"Generated account number {} [attempt={}/{}]",
					candidate,
					attempt,
					MAX_COLLISION_RETRIES
				);

				return candidate;
			}

			log.error(
				"Account number collision detected [candidate={}, attempt={}/{} severity=HIGH].",
				candidate,
				attempt,
				MAX_COLLISION_RETRIES
			);
		}

		throw new AccountNumberGenerationException(MAX_COLLISION_RETRIES);
	}
}
