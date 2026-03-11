package com.miqu3iasg.banking_mvp.shared.support;

import com.miqu3iasg.banking.BankingMvpApplication;
import com.miqu3iasg.banking.account.api.dto.AccountResponse;
import com.miqu3iasg.banking.account.api.dto.CreateAccountRequest;
import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.domain.AccountAction;
import com.miqu3iasg.banking.account.domain.AccountType;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.account.service.AccountService;
import com.miqu3iasg.banking.pix.repository.PixChargeRepository;
import com.miqu3iasg.banking.pix.repository.PixKeyRepository;
import com.miqu3iasg.banking.pix.service.PixService;
import com.miqu3iasg.banking.shared.idempotency.IdempotencyKey;
import com.miqu3iasg.banking.shared.idempotency.IdempotencyKeyRepository;
import com.miqu3iasg.banking.transaction.domain.Transaction;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import com.miqu3iasg.banking.transaction.repository.TransactionRepository;
import com.miqu3iasg.banking.transaction.service.DepositService;
import com.miqu3iasg.banking.transaction.service.WithdrawalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Testcontainers
@SpringBootTest(
	classes = BankingMvpApplication.class,
	webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("integration-test")
public abstract class AbstractIntegrationTestSupport {

	protected static final String CPF_1 = "529.982.247-25";
	protected static final String CPF_2 = "111.444.777-35";
	protected static final String CPF_3 = "222.333.444-05";

	protected static final Duration IDEMPOTENCY_KEY_RETENTION = Duration.ofHours(24);

	/**
	 * Total threads spawned in standard concurrent scenarios.
	 */
	protected static final int CONCURRENT_THREADS = 12;

	/**
	 * Threads used in race-condition scenarios (lower to keep tests deterministic).
	 */
	protected static final int RACING_THREADS = 10;

	/**
	 * Maximum wall-clock seconds we wait for any single Future before failing the test.
	 */
	protected static final long FUTURE_TIMEOUT_SECONDS = 30;

	protected static final String BRL = "BRL";

	protected static final BigDecimal AMOUNT_SUB_CENT = new BigDecimal("0.0001");
	protected static final BigDecimal AMOUNT_MIN = new BigDecimal("0.01");
	protected static final BigDecimal AMOUNT_1_00 = new BigDecimal("1.00");
	protected static final BigDecimal AMOUNT_100 = new BigDecimal("100.00");
	protected static final BigDecimal AMOUNT_123_45 = new BigDecimal("123.45");
	protected static final BigDecimal AMOUNT_200 = new BigDecimal("200.00");
	protected static final BigDecimal AMOUNT_300 = new BigDecimal("300.00");
	protected static final BigDecimal AMOUNT_500 = new BigDecimal("500.00");
	protected static final BigDecimal AMOUNT_LARGE = new BigDecimal("999.9999");

	protected static final BigDecimal STANDARD = AMOUNT_500;

	@Autowired
	protected AccountService accountService;
	@Autowired
	protected AccountRepository accountRepository;
	@Autowired
	protected TransactionRepository transactionRepository;
	@Autowired
	protected IdempotencyKeyRepository idempotencyKeyRepository;
	@Autowired
	protected DepositService depositService;
	@Autowired
	protected WithdrawalService withdrawalService;
	@Autowired
	protected CacheManager cacheManager;
	@Autowired
	protected PixService pixService;
	@Autowired
	protected PixChargeRepository chargeRepository;
	@Autowired
	protected PixKeyRepository keyRepository;

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
		.withDatabaseName("banking_test")
		.withUsername("test")
		.withPassword("test")
		.withReuse(true);

	@DynamicPropertySource
	static void configureDataSource (DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

		// Disable outbox processor to prevent background noise during assertions
		registry.add("outbox.processor.enabled", () -> "false");
	}

	protected static CreateAccountRequest checkingRequest (String document) {
		return new CreateAccountRequest(
			"John Doe",
			document,
			AccountType.CHECKING,
			"holder@example.com"
		);
	}

	protected static CreateAccountRequest savingsRequest (String document) {
		return new CreateAccountRequest(
			"Jane Doe",
			document,
			AccountType.SAVINGS,
			"savings@example.com"
		);
	}

	protected AccountResponse openChecking (String document) {
		return accountService.openAccount(checkingRequest(document));
	}

	protected AccountResponse openThenBlock (String document) {
		AccountResponse account = openChecking(document);
		return accountService.applyStatusAction(account.id(), AccountAction.BLOCK_ACCOUNT_USAGE);
	}

	protected AccountResponse openThenClose (String document) {
		AccountResponse account = openChecking(document);
		return accountService.applyStatusAction(account.id(), AccountAction.CLOSE_ACCOUNT);
	}

	protected String generateCpf (int seed) {
		int base = (seed + 1) * 13;
		int[] digits = new int[11];
		for (int i = 8; i >= 0; i--) {
			digits[i] = base % 10;
			base /= 10;
		}

		int sum = 0;
		for (int i = 0; i < 9; i++) sum += digits[i] * (10 - i);
		int remainder = sum % 11;
		digits[9] = remainder < 2 ? 0 : 11 - remainder;

		sum = 0;
		for (int i = 0; i < 10; i++) sum += digits[i] * (11 - i);
		remainder = sum % 11;
		digits[10] = remainder < 2 ? 0 : 11 - remainder;

		return String.format("%d%d%d.%d%d%d.%d%d%d-%d%d",
			digits[0], digits[1], digits[2],
			digits[3], digits[4], digits[5],
			digits[6], digits[7], digits[8],
			digits[9], digits[10]);
	}

	protected Account loadAccount (UUID accountId) {
		return accountRepository.findById(accountId)
			.orElseThrow(() -> new AssertionError(
				"Expected account [" + accountId + "] to exist, but it was not found. " +
					"Check setUp/tearDown ordering or transaction rollback config."));
	}

	protected BigDecimal loadBalance (UUID accountId) {
		return loadAccount(accountId).getBalance().amount();
	}

	protected BigDecimal balanceOf (UUID accountId) {
		return loadBalance(accountId);
	}

	protected BigDecimal sumTransactionAmountsByType (UUID accountId, TransactionType type) {
		return transactionRepository.findByAccountId(accountId).stream()
			.filter(tx -> tx.getType() == type)
			.map(tx -> tx.getAmount().amount())
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	protected Transaction requireTransaction (UUID id, String operationContext) {
		return transactionRepository.findById(id)
			.orElseThrow(() -> new AssertionError(
				"Transaction row must exist after [" + operationContext + "] but was not found: id=" + id));
	}

	protected Transaction requireTransactionByKey (String key) {
		return transactionRepository.findByIdempotencyKey(key)
			.orElseThrow(() -> new AssertionError(
				"findByIdempotencyKey returned empty — index or mapping may be broken: key=" + key));
	}

	protected IdempotencyKey requireIdempotencyRecord (String key, String operationContext) {
		return idempotencyKeyRepository.findByKey(key)
			.orElseThrow(() -> new AssertionError(
				"Idempotency record must be persisted after a successful [" + operationContext + "]: key=" + key));
	}

	protected void clearAllCaches () {
		cacheManager.getCacheNames().stream()
			.map(cacheManager::getCache)
			.filter(Objects::nonNull)
			.forEach(Cache::clear);
	}

	protected ConcurrentTestResult runConcurrent (int threadCount, ConcurrentTask task)
		throws InterruptedException, ExecutionException, TimeoutException {

		CountDownLatch ready = new CountDownLatch(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger successes = new AtomicInteger();
		CopyOnWriteArrayList<Throwable> failures = new CopyOnWriteArrayList<>();

		try (ExecutorService pool = Executors.newFixedThreadPool(threadCount)) {
			List<Future<Void>> futures = IntStream.range(0, threadCount)
				.mapToObj(i -> pool.submit((Callable<Void>) () -> {
					ready.countDown();
					start.await(); // wait until all threads are ready to fire together

					try {
						task.execute(i);
						successes.incrementAndGet();
					} catch (Exception e) {
						failures.add(e);
					}

					return null;
				}))
				.toList();

			ready.await();
			start.countDown();

			for (Future<Void> f : futures) {
				f.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			}
		}

		return new ConcurrentTestResult(successes.get(), List.copyOf(failures));
	}

	@FunctionalInterface
	protected interface ConcurrentTask {
		void execute (int threadIndex) throws Exception;
	}

	protected record ConcurrentTestResult(int successes, List<Throwable> failures) { }
}
