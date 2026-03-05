package com.miqu3iasg.banking_mvp.transaction.service;

import com.miqu3iasg.banking.account.api.dto.AccountResponse;
import com.miqu3iasg.banking.account.api.dto.TransactionResponse;
import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.shared.domain.Money;
import com.miqu3iasg.banking.transaction.api.dto.DepositRequest;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import com.miqu3iasg.banking.transaction.service.DepositService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DepositServiceIntegrationTest extends AbstractIntegrationTestSupport {

	@Autowired
	DepositService depositService;

	@Test
	@Transactional
	@DisplayName("Should perform a deposit and return correct response")
	void shouldPerformDepositAndReturnCorrectResponse () {
		AccountResponse account = openChecking(CPF_1);
		DepositRequest request = createDepositRequest(account.id());

		TransactionResponse response = depositService.deposit(generateIdempotencyKey(), request);

		Account accountAfterDeposit = accountRepository.findById(account.id()).orElseThrow();

		assertThat(response).isNotNull();
		assertThat(response.transactionId()).isNotNull();
		assertThat(response.accountId()).isEqualTo(account.id());
		assertThat(response.type()).isEqualTo(TransactionType.CREDIT);
		assertThat(response.amount()).isEqualByComparingTo(request.amount());
		assertThat(response.currency()).isEqualTo(request.currency());

		BigDecimal expectedBalance = BigDecimal.valueOf(100);
		assertThat(accountAfterDeposit.getBalance().amount()).isEqualByComparingTo(expectedBalance);
	}

	@RepeatedTest(5)
	@DisplayName("Should handle duplicate deposit requests idempotently")
	void shouldHandleDuplicateDepositRequestsIdempotently () throws Exception {
		int threads = 8;

		// I need to create a valid idempotency key
		UUID accountId = openChecking(CPF_1).id();

		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger successes = new AtomicInteger();
		List<Exception> unexpected = new CopyOnWriteArrayList<>();

		try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
			List<Future<Void>> futures = new ArrayList<>();

			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					try {
						depositService.deposit(generateIdempotencyKey(), createDepositRequest(accountId));
						successes.incrementAndGet();
					} catch (Exception e) {
						System.out.println("Request failed with exception: " + e.getMessage());
						unexpected.add(e);
					}
					return null;
				}));
			}

			ready.await();
			start.countDown();

			for (Future<Void> f : futures) {
				f.get(30, TimeUnit.SECONDS);
			}
		}

		assertThat(successes.get()).isEqualTo(1);

		assertThat(unexpected).isEmpty();

		Account accountAfterDeposit = accountRepository.findById(accountId).orElseThrow();
		BigDecimal expectedBalance = BigDecimal.valueOf(100);
		assertThat(accountAfterDeposit.getBalance().amount()).isEqualByComparingTo(expectedBalance);
	}

	private DepositRequest createDepositRequest (UUID accountId) {
		return new DepositRequest(
			accountId,
			BigDecimal.valueOf(100),
			Money.BRL.getCurrencyCode(),
			"Test deposit"
		);
	}

	private String generateIdempotencyKey () {
		return UUID.randomUUID().toString();
	}
}
