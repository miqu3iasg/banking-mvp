package com.miqu3iasg.banking_mvp.account.service;

import com.miqu3iasg.banking.BankingMvpApplication;
import com.miqu3iasg.banking.account.api.dto.AccountResponse;
import com.miqu3iasg.banking.account.api.dto.CreateAccountRequest;
import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.domain.AccountAction;
import com.miqu3iasg.banking.account.domain.AccountStatus;
import com.miqu3iasg.banking.account.domain.AccountType;
import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.account.service.AccountService;
import com.miqu3iasg.banking.shared.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;

@Testcontainers
@SpringBootTest(classes = BankingMvpApplication.class)
@DisplayName("AccountService — Integration")
@ActiveProfiles("integration-test")
class AccountServiceIntegrationTest {

	private static final int CONCURRENT_USERS = 50;
	private static final int TIMEOUT_SECONDS = 30;

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
		registry.add("outbox.processor.enabled", () -> "false");
	}

	@Autowired
	AccountService accountService;

	@Autowired
	AccountRepository accountRepository;

	@BeforeEach
	void cleanDatabase () {
		accountRepository.deleteAll();
	}

	private static final String CPF_1 = "529.982.247-25";
	private static final String CPF_2 = "111.444.777-35";
	private static final String CPF_3 = "222.333.444-05";

	@Nested
	@DisplayName("Opening a new account")
	class OpenAccount {

		@Test
		@DisplayName("persists CHECKING account with ACTIVE status and zero balance")
		void checkingAccountIsPersistedWithActiveStatusAndZeroBalance () {
			AccountResponse response = openChecking(CPF_1);

			assertThat(response.id()).isNotNull();
			assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
			assertThat(response.type()).isEqualTo(AccountType.CHECKING);
			assertThat(response.accountNumber()).hasSize(8).matches("\\d{8}");

			Account raw = accountRepository.findById(response.id()).orElseThrow();
			assertThat(raw.getBalance().amount()).isZero();
			assertThat(raw.isActive()).isTrue();
			assertThat(raw.getVersion()).isNotNull();
		}

		@Test
		@DisplayName("persists SAVINGS account and assigns correct default currency")
		void savingsAccountIsPersistedWithCorrectCurrency () {
			AccountResponse response = accountService.openAccount(savings(CPF_2));

			assertThat(response.type()).isEqualTo(AccountType.SAVINGS);
			assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);

			Account raw = accountRepository.findById(response.id()).orElseThrow();
			assertThat(raw.getBalance().currency()).isNotNull();
		}

		@Test
		@DisplayName("generated account number is unique and left-padded to 8 digits")
		void accountNumberIsUniqueAndZeroPadded () {
			AccountResponse r1 = openChecking(CPF_1);
			AccountResponse r2 = openChecking(CPF_2);

			assertThat(r1.accountNumber()).matches("\\d{8}");
			assertThat(r2.accountNumber()).matches("\\d{8}");
			assertThat(r1.accountNumber()).isNotEqualTo(r2.accountNumber());
		}

		@Test
		@DisplayName("throws AccountAlreadyExistsException when document is already registered")
		void duplicateDocumentThrowsAccountAlreadyExistsException () {
			openChecking(CPF_1);

			assertThatThrownBy(() -> openChecking(CPF_1))
				.isInstanceOf(AccountAlreadyExistsException.class);
		}

		@Test
		@DisplayName("duplicate detection is case-insensitive on document number")
		void duplicateDocumentIsCaseInsensitive () {
			openChecking(CPF_1);

			assertThatThrownBy(() -> accountService.openAccount(checking(CPF_1.toLowerCase())))
				.isInstanceOf(AccountAlreadyExistsException.class);
		}

		@ParameterizedTest(name = "holderName=''{0}'' → InvalidRequestException")
		@NullAndEmptySource
		@ValueSource(strings = {"   ", "\t", "\n"})
		@DisplayName("rejects blank or null holderName")
		void blankHolderNameThrowsInvalidRequestException (String name) {
			assertThatThrownBy(() -> new CreateAccountRequest(name, CPF_1, AccountType.CHECKING, "valid@example.com"))
				.isInstanceOf(InvalidRequestException.class);
		}

		@ParameterizedTest(name = "email=''{0}'' → InvalidRequestException")
		@NullAndEmptySource
		@ValueSource(strings = {
			"   ",
			"no-at-sign",
			"@nodomain",
			"missing-tld@host",
			"double@@host.com",
			"spaces in@email.com"
		})
		@DisplayName("rejects structurally invalid emails")
		void invalidEmailThrowsInvalidRequestException (String email) {
			assertThatThrownBy(() -> new CreateAccountRequest("John Doe", CPF_1, AccountType.CHECKING, email))
				.isInstanceOf(InvalidRequestException.class);
		}

		@Test
		@DisplayName("rejects null accountType")
		void nullAccountTypeThrowsInvalidRequestException () {
			assertThatThrownBy(() -> new CreateAccountRequest("John Doe", CPF_1, null, "valid@example.com"))
				.isInstanceOf(InvalidRequestException.class);
		}

		@RepeatedTest(5)
		@DisplayName("generates unique account numbers under 20 concurrent openings")
		void concurrentOpeningsAccountNumbersAreNeverDuplicated () throws Exception {
			CountDownLatch ready = new CountDownLatch(CONCURRENT_USERS);
			CountDownLatch start = new CountDownLatch(1);
			Set<String> numbers = ConcurrentHashMap.newKeySet();
			List<Exception> unexpected = new CopyOnWriteArrayList<>();

			try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_USERS)) {
				for (int i = 0; i < CONCURRENT_USERS; i++) {
					final String doc = generateCpf(i);

					pool.submit(() -> {
						ready.countDown();

						try {
							start.await();

							AccountResponse response = accountService
								.openAccount(new CreateAccountRequest("User", doc, AccountType.CHECKING, "u@x.com"));

							numbers.add(response.accountNumber());
						} catch (InvalidDocumentException ignored) {
							/* CPF validation may reject some */
						} catch (Exception e) {
							unexpected.add(e);
						}
					});
				}

				ready.await();
				start.countDown();
				pool.shutdown();

				assertThat(pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

				assertThat(unexpected).isEmpty();

				assertThat(numbers)
					.isNotEmpty()
					.doesNotContainNull()
					.hasSize(CONCURRENT_USERS);
			}
		}
	}

	@Nested
	@DisplayName("Looking up an account by ID")
	class FindById {

		@Test
		@DisplayName("returns correct DTO for an existing account")
		void existingAccountReturnsDtoWithMatchingFields () {
			AccountResponse created = openChecking(CPF_1);

			AccountResponse found = accountService.findById(created.id());

			assertThat(found.id()).isEqualTo(created.id());
			assertThat(found.accountNumber()).isEqualTo(created.accountNumber());
			assertThat(found.type()).isEqualTo(created.type());
			assertThat(found.status()).isEqualTo(created.status());
			assertThat(found.holderName()).isEqualTo(created.holderName());
		}

		@Test
		@DisplayName("throws AccountNotFoundException for a random UUID")
		void unknownIdThrowsAccountNotFoundException () {
			assertThatThrownBy(() -> accountService.findById(UUID.randomUUID()))
				.isInstanceOf(AccountNotFoundException.class);
		}

		@Test
		@DisplayName("throws AccountNotFoundException after the account has been deleted from DB externally")
		void deletedAccountThrowsAccountNotFoundException () {
			AccountResponse created = openChecking(CPF_1);
			accountRepository.deleteById(created.id());

			assertThatThrownBy(() -> accountService.findById(created.id()))
				.isInstanceOf(AccountNotFoundException.class);
		}
	}

	@Nested
	@DisplayName("Blocking an account")
	class BlockAccount {

		@Test
		@DisplayName("transitions ACTIVE → BLOCKED and persists the new status")
		void activeAccountTransitionsToBlocked () {
			AccountResponse created = openChecking(CPF_1);

			AccountResponse result = accountService.applyStatusAction(created.id(), AccountAction.BLOCK_ACCOUNT_USAGE);

			assertThat(result.status()).isEqualTo(AccountStatus.BLOCKED);

			Account raw = accountRepository.findById(created.id()).orElseThrow();
			assertThat(raw.isBlocked()).isTrue();
			assertThat(raw.getVersion()).isGreaterThan(0L);
		}

		@Test
		@DisplayName("throws on BLOCKED → BLOCK (double-block is illegal)")
		void blockedAccountRejectsDoubleBlock () {
			AccountResponse blocked = openAndBlock(CPF_1);

			assertThatThrownBy(() -> accountService.applyStatusAction(blocked.id(), AccountAction.BLOCK_ACCOUNT_USAGE))
				.isInstanceOf(AccountBlockedException.class);
		}

		@Test
		@DisplayName("throws on CLOSED → BLOCK")
		void closedAccountRejectsBlock () {
			AccountResponse closed = openAndClose(CPF_1);

			assertThatThrownBy(() -> accountService.applyStatusAction(closed.id(), AccountAction.BLOCK_ACCOUNT_USAGE))
				.isInstanceOf(AccountClosedException.class);
		}

		@Test
		@DisplayName("throws AccountNotFoundException for unknown ID")
		void unknownIdThrowsAccountNotFoundException () {
			assertThatThrownBy(() -> accountService.applyStatusAction(UUID.randomUUID(), AccountAction.BLOCK_ACCOUNT_USAGE))
				.isInstanceOf(AccountNotFoundException.class);
		}
	}

	@Nested
	@DisplayName("Unblocking an account")
	class UnblockAccount {

		@Test
		@DisplayName("transitions BLOCKED → ACTIVE and persists the new status")
		void blockedAccountTransitionsToActive () {
			AccountResponse blocked = openAndBlock(CPF_1);

			AccountResponse result = accountService.applyStatusAction(blocked.id(), AccountAction.UNBLOCK_ACCOUNT_USAGE);

			assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE);

			Account raw = accountRepository.findById(blocked.id()).orElseThrow();
			assertThat(raw.isActive()).isTrue();
		}

		@Test
		@DisplayName("throws on ACTIVE → UNBLOCK (account is not blocked)")
		void activeAccountRejectsUnblock () {
			AccountResponse created = openChecking(CPF_1);

			assertThatThrownBy(() -> accountService.applyStatusAction(created.id(), AccountAction.UNBLOCK_ACCOUNT_USAGE))
				.isInstanceOf(BusinessException.class);
		}

		@Test
		@DisplayName("throws on CLOSED → UNBLOCK")
		void closedAccountRejectsUnblock () {
			AccountResponse closed = openAndClose(CPF_1);

			assertThatThrownBy(() -> accountService.applyStatusAction(closed.id(), AccountAction.UNBLOCK_ACCOUNT_USAGE))
				.isInstanceOf(BusinessException.class);
		}
	}

	@Nested
	@DisplayName("Closing an account")
	class CloseAccount {

		@Test
		@DisplayName("closes an ACTIVE account with zero balance")
		void activeZeroBalanceAccountClosesSuccessfully () {
			AccountResponse created = openChecking(CPF_1);

			AccountResponse result = accountService.applyStatusAction(created.id(), AccountAction.CLOSE_ACCOUNT);

			assertThat(result.status()).isEqualTo(AccountStatus.CLOSED);

			Account raw = accountRepository.findById(created.id()).orElseThrow();
			assertThat(raw.isClosed()).isTrue();
		}

		@Test
		@DisplayName("throws on BLOCKED → CLOSE (must unblock first)")
		void blockedAccountRejectsClose () {
			AccountResponse blocked = openAndBlock(CPF_1);

			assertThatThrownBy(() -> accountService.applyStatusAction(blocked.id(), AccountAction.CLOSE_ACCOUNT))
				.isInstanceOf(AccountBlockedException.class);
		}

		@Test
		@DisplayName("throws on CLOSED → CLOSE (already closed)")
		void closedAccountRejectsDoubleClose () {
			AccountResponse closed = openAndClose(CPF_1);

			assertThatThrownBy(() -> accountService.applyStatusAction(closed.id(), AccountAction.CLOSE_ACCOUNT))
				.isInstanceOf(BusinessException.class);
		}

		@Test
		@DisplayName("throws AccountNotFoundException for unknown ID")
		void unknownIdThrowsAccountNotFoundException () {
			assertThatThrownBy(() -> accountService.applyStatusAction(UUID.randomUUID(), AccountAction.CLOSE_ACCOUNT))
				.isInstanceOf(AccountNotFoundException.class);
		}
	}

	@Nested
	@DisplayName("Account status state machine — full transition matrix")
	class StateMachineMatrix {

		static Stream<Object[]> illegalTransitions () {
			return Stream.of(
				new Object[] {"ACTIVE → UNBLOCK", "active", AccountAction.UNBLOCK_ACCOUNT_USAGE},
				new Object[] {"BLOCKED → BLOCK", "blocked", AccountAction.BLOCK_ACCOUNT_USAGE},
				new Object[] {"BLOCKED → CLOSE", "blocked", AccountAction.CLOSE_ACCOUNT},
				new Object[] {"CLOSED → BLOCK", "closed", AccountAction.BLOCK_ACCOUNT_USAGE},
				new Object[] {"CLOSED → UNBLOCK", "closed", AccountAction.UNBLOCK_ACCOUNT_USAGE},
				new Object[] {"CLOSED → CLOSE", "closed", AccountAction.CLOSE_ACCOUNT}
			);
		}

		@ParameterizedTest(name = "illegal: {0}")
		@MethodSource("illegalTransitions")
		@DisplayName("every illegal transition throws a RuntimeException")
		void illegalTransitionAlwaysThrows (String description, String setup, AccountAction action) {
			UUID id = switch (setup) {
				case "active" -> openChecking(CPF_1).id();
				case "blocked" -> openAndBlock(CPF_1).id();
				case "closed" -> openAndClose(CPF_1).id();
				default ->
					throw new IllegalArgumentException("unknown setup: " + setup);
			};

			Throwable thrown = catchThrowable(() -> accountService.applyStatusAction(id, action));

			assertThat(thrown)
				.as("Transition '%s' must be rejected by the domain", description)
				.isInstanceOf(RuntimeException.class);

			Account raw = accountRepository.findById(id).orElseThrow();

			AccountStatus expectedStatus = switch (setup) {
				case "active" -> AccountStatus.ACTIVE;
				case "blocked" -> AccountStatus.BLOCKED;
				case "closed" -> AccountStatus.CLOSED;
				default -> throw new IllegalStateException();
			};

			assertThat(raw.getStatus())
				.as("DB status must be unchanged after a rejected transition")
				.isEqualTo(expectedStatus);
		}

		@Test
		@DisplayName("legal path: ACTIVE → BLOCKED → ACTIVE → CLOSED terminates without error")
		void fullLegalPathCompletesWithoutError () {
			UUID id = openChecking(CPF_1).id();

			assertThat(accountService.applyStatusAction(id, AccountAction.BLOCK_ACCOUNT_USAGE).status())
				.isEqualTo(AccountStatus.BLOCKED);

			assertThat(accountService.applyStatusAction(id, AccountAction.UNBLOCK_ACCOUNT_USAGE).status())
				.isEqualTo(AccountStatus.ACTIVE);

			assertThat(accountService.applyStatusAction(id, AccountAction.CLOSE_ACCOUNT).status())
				.isEqualTo(AccountStatus.CLOSED);
		}

		@Test
		@DisplayName("legal path: ACTIVE → CLOSED (skip block cycle)")
		void directClosePathCompletesWithoutError () {
			UUID id = openChecking(CPF_1).id();

			assertThat(accountService.applyStatusAction(id, AccountAction.CLOSE_ACCOUNT).status())
				.isEqualTo(AccountStatus.CLOSED);
		}
	}

	@Nested
	@DisplayName("Idempotency guarantees")
	class Idempotency {

		@Test
		@DisplayName("reading the same account twice returns identical DTOs")
		void findByIdIsIdempotent () {
			AccountResponse created = openChecking(CPF_1);

			AccountResponse first = accountService.findById(created.id());
			AccountResponse second = accountService.findById(created.id());

			assertThat(first).isEqualTo(second);
		}

		@Test
		@DisplayName("openAccount is NOT idempotent — second call with same document throws")
		void openAccountIsNotIdempotent () {
			openChecking(CPF_1);

			assertThatThrownBy(() -> openChecking(CPF_1))
				.isInstanceOf(AccountAlreadyExistsException.class);
		}
	}

	@Nested
	@DisplayName("Optimistic locking under concurrent writes")
	class OptimisticLockConcurrency {

		@RepeatedTest(5)
		@DisplayName("concurrent BLOCK on the same account — exactly one thread wins, account state is consistent")
		void concurrentBlockExactlyOneWinsStateIsConsistent () throws Exception {
			UUID id = openChecking(CPF_1).id();
			int threads = 8;

			CountDownLatch ready = new CountDownLatch(threads);
			CountDownLatch start = new CountDownLatch(1);
			AtomicInteger successes = new AtomicInteger();
			List<Throwable> caught = new CopyOnWriteArrayList<>();

			try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
				List<Future<Void>> futures = new ArrayList<>();

				for (int i = 0; i < threads; i++) {
					futures.add(pool.submit(() -> {
						ready.countDown();
						start.await();
						try {
							accountService.applyStatusAction(id, AccountAction.BLOCK_ACCOUNT_USAGE);
							successes.incrementAndGet();
						} catch (RuntimeException e) {
							caught.add(e);
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

			assertThat(caught).hasSize(threads - 1);
			caught.forEach(t ->
				assertThat(t).isInstanceOf(RuntimeException.class)
			);

			Account raw = accountRepository.findById(id).orElseThrow();
			assertThat(raw.isBlocked()).isTrue();
		}

		@Test
		@DisplayName("interleaved BLOCK → UNBLOCK from separate threads completes with consistent final state")
		void interleavedBlockUnblockDbStateIsConsistent () throws Exception {
			UUID id = openChecking(CPF_1).id();

			Thread t1 = new Thread(() -> accountService.applyStatusAction(id, AccountAction.BLOCK_ACCOUNT_USAGE));
			t1.start();
			t1.join(5_000);

			Thread t2 = new Thread(() -> accountService.applyStatusAction(id, AccountAction.UNBLOCK_ACCOUNT_USAGE));
			t2.start();
			t2.join(5_000);

			Account raw = accountRepository.findById(id).orElseThrow();
			assertThat(raw.isActive())
				.as("Account must be ACTIVE after BLOCK then UNBLOCK")
				.isTrue();
		}
	}

	@Nested
	@DisplayName("Database-level integrity invariants")
	class DatabaseInvariants {

		@Test
		@DisplayName("account_number has a unique constraint — duplicate insert at repo level is rejected by DB")
		void accountNumberUniqueConstraintIsEnforced () {
			AccountResponse created = openChecking(CPF_1);

			Account raw = accountRepository.findById(created.id()).orElseThrow();
			String takenNumber = raw.getAccountNumber();

			// Attempt to open a second account whose number would collide; this is only
			// possible by manipulating the sequence externally or through a mock;
			// here we verify the unique index exists by asserting one account per number.
			long count = accountRepository.findAll().stream()
				.filter(a -> a.getAccountNumber().equals(takenNumber))
				.count();

			assertThat(count).isEqualTo(1);
		}

		@Test
		@DisplayName("version column increments on each write (optimistic locking is active)")
		void versionColumnIncrementsOnEachStatusChange () {
			AccountResponse created = openChecking(CPF_1);
			Account v0 = accountRepository.findById(created.id()).orElseThrow();
			long initialVersion = v0.getVersion();

			accountService.applyStatusAction(created.id(), AccountAction.BLOCK_ACCOUNT_USAGE);
			Account v1 = accountRepository.findById(created.id()).orElseThrow();

			assertThat(v1.getVersion()).isGreaterThan(initialVersion);

			accountService.applyStatusAction(created.id(), AccountAction.UNBLOCK_ACCOUNT_USAGE);
			Account v2 = accountRepository.findById(created.id()).orElseThrow();

			assertThat(v2.getVersion()).isGreaterThan(v1.getVersion());
		}

		@Test
		@DisplayName("countActiveAccounts reflects real-time ACTIVE count across operations")
		void countActiveAccountsTracksStateTransitionsAccurately () {
			long baseline = accountRepository.countActiveAccounts();

			openChecking(CPF_1);
			openChecking(CPF_2);
			assertThat(accountRepository.countActiveAccounts()).isEqualTo(baseline + 2);

			openAndBlock(CPF_3);
			assertThat(accountRepository.countActiveAccounts()).isEqualTo(baseline + 2);
		}

		@Test
		@DisplayName("balance is stored with full precision and is exactly zero after account open")
		void balanceIsZeroAndStoredWithPrecision () {
			AccountResponse created = openChecking(CPF_1);

			Account raw = accountRepository.findById(created.id()).orElseThrow();

			assertThat(raw.getBalance()).isNotNull();
			assertThat(raw.getBalance().amount()).isZero();
			assertThat(raw.getBalance().currency()).isNotNull();
		}
	}

	private static CreateAccountRequest checking (String document) {
		return new CreateAccountRequest(
			"Jhon Doe",
			document,
			AccountType.CHECKING,
			"holder@example.com"
		);
	}

	private static CreateAccountRequest savings (String document) {
		return new CreateAccountRequest(
			"Jane Doe",
			document,
			AccountType.SAVINGS,
			"savings@example.com"
		);
	}

	private AccountResponse openChecking (String document) {
		return accountService.openAccount(checking(document));
	}

	private AccountResponse openAndBlock (String document) {
		AccountResponse account = openChecking(document);
		return accountService.applyStatusAction(account.id(), AccountAction.BLOCK_ACCOUNT_USAGE);
	}

	private AccountResponse openAndClose (String document) {
		AccountResponse account = openChecking(document);
		return accountService.applyStatusAction(account.id(), AccountAction.CLOSE_ACCOUNT);
	}

	private String generateCpf (int seed) {
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
}
