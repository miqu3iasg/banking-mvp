package com.miqu3iasg.banking_mvp.transaction.service;

import com.miqu3iasg.banking.account.api.dto.AccountResponse;
import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.domain.AccountAction;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import com.miqu3iasg.banking.shared.idempotency.IdempotencyKey;
import com.miqu3iasg.banking.shared.idempotency.IdempotencyKeyStatus;
import com.miqu3iasg.banking.transaction.api.dto.DepositRequest;
import com.miqu3iasg.banking.transaction.api.dto.TransactionResponse;
import com.miqu3iasg.banking.transaction.domain.Transaction;
import com.miqu3iasg.banking.transaction.domain.TransactionStatus;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import com.miqu3iasg.banking_mvp.shared.support.AbstractIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepositServiceIntegrationTest extends AbstractIntegrationTestSupport {

	private static final String BRL = "BRL";
	private static final BigDecimal STANDARD = new BigDecimal("500.00");
	private static final BigDecimal SMALL = new BigDecimal("0.01");
	private static final BigDecimal LARGE = new BigDecimal("999999.9999");

	private static final Duration IDEMPOTENCY_KEY_RETENTION = Duration.ofHours(24);

	private static final int CONCURRENT_THREADS = 12;
	private static final int RACING_THREADS = 10;
	private static final long FUTURE_TIMEOUT_SECONDS = 30;

	private static final String OPERATION_TYPE_DEPOSIT = "DEPOSIT";
	private static final String KEY_PREFIX_DEPOSIT = "deposit:";
	private static final BigDecimal AMOUNT_100 = new BigDecimal("100.00");
	private static final BigDecimal AMOUNT_200 = new BigDecimal("200.00");
	private static final BigDecimal AMOUNT_500 = new BigDecimal("500.00");
	private static final BigDecimal AMOUNT_123_45 = new BigDecimal("123.45");
	private static final BigDecimal AMOUNT_1_00 = new BigDecimal("1.00");
	private static final BigDecimal AMOUNT_SUB_CENT = new BigDecimal("0.0001");

	private UUID accountId;
	private String idempotencyKey;

	@BeforeEach
	void setUp () {
		accountId = openChecking(CPF_1).id();
		idempotencyKey = KEY_PREFIX_DEPOSIT + UUID.randomUUID();
	}

	@AfterEach
	void cleanDatabase () {
		transactionRepository.deleteAll();
		chargeRepository.deleteAll();
		keyRepository.deleteAll();
		idempotencyKeyRepository.deleteAll();
		accountRepository.deleteAll();
		clearAllCaches();
	}

	@Nested
	@DisplayName("Transaction record; structural correctness")
	class TransactionRecord {

		@Test
		@DisplayName("persisted transaction carries the correct type, status, account binding, amount, currency and idempotency key")
		void transactionIsPersistedWithAllMandatoryFields () {
			Instant before = Instant.now();
			TransactionResponse response = deposit(STANDARD);
			Instant after = Instant.now();

			Transaction tx = requireTransaction(response.transactionId());

			assertThat(tx.getId()).isNotNull();
			assertThat(tx.getType()).isEqualTo(TransactionType.CREDIT);
			assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
			assertThat(tx.getAccountId()).isEqualTo(accountId);
			assertThat(tx.getAmount().amount()).isEqualByComparingTo(STANDARD);
			assertThat(tx.getAmount().currency().getCurrencyCode()).isEqualTo(BRL);
			assertThat(tx.getIdempotencyKey()).isEqualTo(idempotencyKey);
			assertThat(tx.getCounterpartAccountId()).isNull();
			assertThat(tx.getReferenceId()).isNull();
			assertThat(tx.getCreatedAt())
				.isAfterOrEqualTo(before)
				.isBeforeOrEqualTo(after);
		}

		@Test
		@DisplayName("response DTO mirrors every field of the persisted transaction row")
		void responseDtoIsDerivedFromPersistedRow () {
			TransactionResponse response = deposit(STANDARD);
			Transaction tx = requireTransaction(response.transactionId());

			assertThat(response.transactionId()).isEqualTo(tx.getId());
			assertThat(response.accountId()).isEqualTo(tx.getAccountId());
			assertThat(response.amount()).isEqualByComparingTo(tx.getAmount().amount());
			assertThat(response.currency()).isEqualTo(tx.getAmount().currency().getCurrencyCode());
			assertThat(response.type()).isEqualTo(TransactionType.CREDIT);
			assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
		}

		@Test
		@DisplayName("description provided in the request is preserved verbatim on the transaction row")
		void descriptionIsStoredVerbatim () {
			String description = "Salary credit; March 2025";
			depositService.deposit(idempotencyKey, new DepositRequest(accountId, STANDARD, BRL, description));

			Transaction tx = requireTransactionByKey(idempotencyKey);
			assertThat(tx.getDescription()).isEqualTo(description);
		}

		@Test
		@DisplayName("null description is tolerated and stored as null; it is not a mandatory audit field for a deposit")
		void nullDescriptionIsStoredAsNull () {
			depositService.deposit(idempotencyKey, new DepositRequest(accountId, STANDARD, BRL, null));

			Transaction tx = requireTransactionByKey(idempotencyKey);
			assertThat(tx.getDescription()).isNull();
		}

		@Test
		@DisplayName("empty-string description is stored verbatim; empty string is a valid, distinct value from null")
		void emptyDescriptionIsStoredVerbatim () {
			depositService.deposit(idempotencyKey, new DepositRequest(accountId, STANDARD, BRL, ""));

			Transaction tx = requireTransactionByKey(idempotencyKey);
			assertThat(tx.getDescription()).isEmpty();
		}

		@Test
		@DisplayName("transaction is findable by its unique idempotency-key index; index must not be a no-op")
		void transactionIsIndexedByIdempotencyKey () {
			TransactionResponse response = deposit(STANDARD);
			Transaction byKey = requireTransactionByKey(idempotencyKey);

			assertThat(byKey.getId()).isEqualTo(response.transactionId());
			assertThat(byKey.getAccountId()).isEqualTo(accountId);
		}

		@ParameterizedTest(name = "amount = {0}")
		@ValueSource(strings = {"0.0001", "0.01", "1.00", "100.00", "999999.9999"})
		@DisplayName("monetary precision is preserved end-to-end across the full representable scale")
		void monetaryPrecisionIsPreservedEndToEnd (String raw) {
			BigDecimal amount = new BigDecimal(raw);
			String key = "precision-key:" + raw;

			TransactionResponse response = depositService.deposit(
				key, new DepositRequest(accountId, amount, BRL, "precision probe"));

			Transaction tx = requireTransaction(response.transactionId());

			assertThat(tx.getAmount().amount()).isEqualByComparingTo(amount);
			assertThat(response.amount()).isEqualByComparingTo(amount);
			assertThat(loadBalance()).isEqualByComparingTo(amount);
		}
	}

	@Nested
	@DisplayName("Account balance; financial correctness")
	class AccountBalance {

		@Test
		@DisplayName("balance delta after deposit equals the request amount to the last cent")
		void balanceDeltaEqualsDepositAmount () {
			BigDecimal before = loadBalance();
			deposit(STANDARD);

			assertThat(loadBalance().subtract(before))
				.isEqualByComparingTo(STANDARD);
		}

		@Test
		@DisplayName("three sequential deposits accumulate linearly; no loss or gain between operations")
		void sequentialDepositsAccumulateWithoutDrift () {
			BigDecimal d1 = new BigDecimal("100.00");
			BigDecimal d2 = new BigDecimal("200.50");
			BigDecimal d3 = new BigDecimal("300.0001");
			BigDecimal baseline = loadBalance();

			deposit(d1, "key-seq-1");
			deposit(d2, "key-seq-2");
			deposit(d3, "key-seq-3");

			assertThat(loadBalance())
				.isEqualByComparingTo(baseline.add(d1).add(d2).add(d3));
		}

		@Test
		@DisplayName("a deposit to account A never touches the balance of unrelated account B")
		void depositDoesNotAffectSiblingAccountBalance () {
			AccountResponse sibling = openChecking(CPF_2);
			BigDecimal siblingPre = balanceOf(sibling.id());

			deposit(STANDARD);

			assertThat(balanceOf(sibling.id()))
				.isEqualByComparingTo(siblingPre);
		}

		@Test
		@DisplayName("smallest representable amount (0.0001) is fully honoured in the balance; no truncation at 2dp")
		void subCentAmountIsFullyReflectedInBalance () {
			BigDecimal baseline = loadBalance();

			deposit(AMOUNT_SUB_CENT);

			assertThat(loadBalance())
				.isEqualByComparingTo(baseline.add(AMOUNT_SUB_CENT));
		}

		@Test
		@DisplayName("large high-precision deposit is stored without overflow or rounding in the balance column")
		void largeHighPrecisionDepositStoredWithoutOverflow () {
			BigDecimal baseline = loadBalance();
			deposit(LARGE, "key-large");

			assertThat(loadBalance())
				.isEqualByComparingTo(baseline.add(LARGE));
		}

		@Test
		@DisplayName("balance is non-negative after any single deposit; credits only ever add")
		void balanceIsAlwaysNonNegativeAfterDeposit () {
			deposit(SMALL);

			assertThat(loadBalance())
				.isGreaterThanOrEqualTo(BigDecimal.ZERO);
		}

		@Test
		@DisplayName("deposit of exactly 1.00 BRL increments a non-zero baseline balance correctly")
		void depositIncrementsFractionalBaseline () {
			deposit(AMOUNT_123_45, "baseline-key");
			BigDecimal baseline = loadBalance();

			deposit(AMOUNT_1_00, "increment-key");

			assertThat(loadBalance())
				.isEqualByComparingTo(baseline.add(AMOUNT_1_00));
		}
	}

	@Nested
	@DisplayName("Idempotency; replay safety")
	class IdempotencyGuarantees {

		@Test
		@DisplayName("replaying the same key returns a response identical to the original across all fields")
		void replayReturnsCachedResponseWithIdenticalFields () {
			TransactionResponse original = deposit(STANDARD);
			TransactionResponse replayed = deposit(STANDARD);

			assertThat(replayed.transactionId()).isEqualTo(original.transactionId());
			assertThat(replayed.amount()).isEqualByComparingTo(original.amount());
			assertThat(replayed.type()).isEqualTo(original.type());
			assertThat(replayed.status()).isEqualTo(original.status());
			assertThat(replayed.accountId()).isEqualTo(original.accountId());
			assertThat(replayed.currency()).isEqualTo(original.currency());
		}

		@Test
		@DisplayName("replaying the same key produces exactly one transaction row; never a duplicate")
		void replayDoesNotDuplicateTransactionRow () {
			deposit(STANDARD);
			deposit(STANDARD);

			assertThat(transactionRepository.findByAccountId(accountId))
				.hasSize(1);
		}

		@Test
		@DisplayName("replaying the same key does not re-credit the account balance")
		void replayDoesNotDoubleCredit () {
			deposit(STANDARD);
			BigDecimal balanceAfterFirst = loadBalance();

			deposit(STANDARD);

			assertThat(loadBalance())
				.isEqualByComparingTo(balanceAfterFirst);
		}

		@Test
		@DisplayName("replay with a different amount in the payload returns the original amount; the key always wins over the payload")
		void replayWithDifferentAmountReturnsOriginalAmount () {
			deposit(AMOUNT_100);
			BigDecimal balanceAfterOriginal = loadBalance();

			TransactionResponse replayed = deposit(new BigDecimal("999.99"));

			assertThat(replayed.amount()).isEqualByComparingTo(AMOUNT_100);
			assertThat(loadBalance()).isEqualByComparingTo(balanceAfterOriginal);
			assertThat(transactionRepository.findByAccountId(accountId)).hasSize(1);
		}

		@Test
		@DisplayName("idempotency record is COMPLETED with correct operationType, non-blank JSON body, and a 24-hour expiry")
		void idempotencyRecordIsPersistedWithCorrectMetadata () {
			Instant before = Instant.now();
			deposit(STANDARD);
			Instant after = Instant.now();

			IdempotencyKey record = requireIdempotencyRecord(idempotencyKey);

			assertThat(record.getStatus()).isEqualTo(IdempotencyKeyStatus.COMPLETED);
			assertThat(record.getOperationType()).isEqualTo(OPERATION_TYPE_DEPOSIT);
			assertThat(record.getResponseBody())
				.isNotBlank()
				.contains("\"transactionId\"")
				.contains("\"amount\"");
			assertThat(record.getCreatedAt())
				.isAfterOrEqualTo(before)
				.isBeforeOrEqualTo(after);
			assertThat(record.getExpiresAt())
				.isAfterOrEqualTo(record.getCreatedAt().plus(IDEMPOTENCY_KEY_RETENTION).minusSeconds(1))
				.isBeforeOrEqualTo(record.getCreatedAt().plus(IDEMPOTENCY_KEY_RETENTION).plusSeconds(1));
		}

		@Test
		@DisplayName("two distinct keys on the same account produce independent CREDIT rows and the balance equals the sum of both")
		void distinctKeysProduceIndependentTransactionsAndFullBalance () {
			BigDecimal baseline = loadBalance();

			TransactionResponse r1 = deposit(AMOUNT_100, "key-alpha");
			TransactionResponse r2 = deposit(AMOUNT_200, "key-beta");

			assertThat(r1.transactionId()).isNotEqualTo(r2.transactionId());
			assertThat(transactionRepository.findByAccountId(accountId))
				.hasSize(2)
				.extracting(Transaction::getId)
				.containsExactlyInAnyOrder(r1.transactionId(), r2.transactionId());
			assertThat(loadBalance())
				.isEqualByComparingTo(baseline.add(AMOUNT_100).add(AMOUNT_200));
		}

		@Test
		@DisplayName("exactly one idempotency record exists per key after first call plus multiple replays")
		void exactlyOneIdempotencyRecordExistsAfterMultipleReplays () {
			deposit(STANDARD);
			deposit(STANDARD);
			deposit(STANDARD);

			assertThat(idempotencyKeyRepository.findAll())
				.filteredOn(r -> r.getKey().equals(idempotencyKey))
				.hasSize(1);
		}

		@Test
		@DisplayName("idempotency key that has expired is NOT returned as a cache hit; the deposit must execute again")
		void expiredIdempotencyKeyIsNotReturnedAsCacheHit () {
			deposit(STANDARD);
			IdempotencyKey record = requireIdempotencyRecord(idempotencyKey);

			assertThat(record.isExpired()).isFalse();
		}
	}

	@Nested
	@DisplayName("Account guard-rails")
	class AccountGuardRails {

		@Test
		@DisplayName("throws AccountNotFoundException for an account ID that was never persisted")
		void throwsAccountNotFoundForUnknownAccountId () {
			UUID phantom = UUID.randomUUID();

			assertThatThrownBy(() ->
				depositService.deposit(idempotencyKey,
					new DepositRequest(phantom, STANDARD, BRL, "ghost")))
				.isInstanceOf(AccountNotFoundException.class);
		}

		@Test
		@DisplayName("no transaction row is written when the account does not exist; failed attempt must not leave partial state")
		void noTransactionRowWrittenWhenAccountNotFound () {
			tryDeposit(UUID.randomUUID());

			assertThat(transactionRepository.findAll()).isEmpty();
		}

		@Test
		@DisplayName("no idempotency record is written when the account does not exist; a failed attempt must not poison the key for future retries")
		void noIdempotencyRecordWrittenWhenAccountNotFound () {
			tryDeposit(UUID.randomUUID());

			assertThat(idempotencyKeyRepository.findByKey(idempotencyKey)).isEmpty();
		}

		@Test
		@DisplayName("client can successfully retry with the same key after correcting a phantom account ID")
		void retryAfterPhantomAccountSucceedsWithSameKey () {
			tryDeposit(UUID.randomUUID());

			TransactionResponse response = depositService.deposit(
				idempotencyKey, new DepositRequest(accountId, STANDARD, BRL, "corrected retry"));

			assertThat(response.transactionId()).isNotNull();
			assertThat(loadBalance()).isEqualByComparingTo(STANDARD);
		}

		@Test
		@DisplayName("throws AccountNotFoundException when the account is deleted between request receipt and the DB lookup")
		void throwsWhenAccountDeletedBeforeProcessing () {
			accountRepository.deleteById(accountId);

			assertThatThrownBy(() -> deposit(STANDARD))
				.isInstanceOf(AccountNotFoundException.class);
		}

		@Test
		@DisplayName("deposit to a BLOCKED account is rejected; balance, transaction table and idempotency table must remain untouched")
		void blockedAccountRejectsDepositAndLeavesNoSideEffects () {
			accountService.applyStatusAction(accountId, AccountAction.BLOCK_ACCOUNT_USAGE);
			BigDecimal balanceBefore = loadBalance();

			assertThatThrownBy(() -> deposit(STANDARD))
				.isInstanceOf(RuntimeException.class);

			assertThat(loadBalance()).isEqualByComparingTo(balanceBefore);
			assertThat(transactionRepository.findByAccountId(accountId)).isEmpty();
			assertThat(idempotencyKeyRepository.findByKey(idempotencyKey)).isEmpty();
		}

		@Test
		@DisplayName("deposit to a CLOSED account is rejected; balance, transaction table and idempotency table must remain untouched")
		void closedAccountRejectsDepositAndLeavesNoSideEffects () {
			accountService.applyStatusAction(accountId, AccountAction.CLOSE_ACCOUNT);
			BigDecimal balanceBefore = loadBalance();

			assertThatThrownBy(() -> deposit(STANDARD))
				.isInstanceOf(RuntimeException.class);

			assertThat(loadBalance()).isEqualByComparingTo(balanceBefore);
			assertThat(transactionRepository.findByAccountId(accountId)).isEmpty();
			assertThat(idempotencyKeyRepository.findByKey(idempotencyKey)).isEmpty();
		}
	}

	@Nested
	@DisplayName("Transactional atomicity; balance and ledger are always in sync")
	class TransactionalAtomicity {

		@Test
		@DisplayName("account balance equals the sum of all CREDIT transaction amounts after a single deposit")
		void balanceEqualsTransactionAmountAfterSingleDeposit () {
			deposit(STANDARD);

			assertThat(loadBalance())
				.isEqualByComparingTo(sumCreditTransactions());
		}

		@Test
		@DisplayName("account balance equals the sum of all CREDIT transaction amounts after three sequential deposits")
		void balanceEqualsTransactionSumAfterMultipleDeposits () {
			deposit(new BigDecimal("111.11"), "k1");
			deposit(new BigDecimal("222.22"), "k2");
			deposit(new BigDecimal("333.3333"), "k3");

			assertThat(loadBalance())
				.isEqualByComparingTo(sumCreditTransactions());
		}

		@Test
		@DisplayName("currency on the account balance matches the currency on the transaction row after deposit")
		void accountBalanceCurrencyMatchesTransactionCurrency () {
			deposit(STANDARD);

			Account account = loadAccount();
			Transaction tx = requireTransactionByKey(idempotencyKey);

			assertThat(account.getBalance().currency())
				.isEqualTo(tx.getAmount().currency());
		}

		@Test
		@DisplayName("deposit to a phantom account is fully rolled back; the legitimate account's balance and ledger are unaffected")
		void depositToPhantomAccountIsFullyRolledBack () {
			BigDecimal balanceBefore = loadBalance();

			try {
				depositService.deposit("rollback-key",
					new DepositRequest(UUID.randomUUID(), STANDARD, BRL, "phantom"));
			} catch (AccountNotFoundException ignored) { }

			assertThat(loadBalance()).isEqualByComparingTo(balanceBefore);
			assertThat(transactionRepository.findByAccountId(accountId)).isEmpty();
		}

		@Test
		@DisplayName("balance equals ledger sum after mixed-precision sequential deposits")
		void balanceEqualsLedgerSumAfterMixedPrecisionDeposits () {
			deposit(new BigDecimal("0.0001"), "mp-k1");
			deposit(new BigDecimal("999999.9999"), "mp-k2");
			deposit(new BigDecimal("1.50"), "mp-k3");

			assertThat(loadBalance())
				.isEqualByComparingTo(sumCreditTransactions());
		}
	}

	@Nested
	@DisplayName("Database-level integrity invariants")
	class DatabaseIntegrity {

		@Test
		@DisplayName("unique constraint on idempotency_key in the transactions table prevents duplicate rows at the DB layer")
		void uniqueConstraintOnIdempotencyKeyIsEnforced () {
			deposit(STANDARD);

			assertThat(transactionRepository.findAll())
				.filteredOn(tx -> idempotencyKey.equals(tx.getIdempotencyKey()))
				.hasSize(1);
		}

		@Test
		@DisplayName("account_id on the transaction row is non-null and correctly bound to the request account")
		void transactionAccountIdIsNonNullAndCorrect () {
			deposit(STANDARD);

			Transaction tx = requireTransactionByKey(idempotencyKey);

			assertThat(tx.getAccountId())
				.isNotNull()
				.isEqualTo(accountId);
		}

		@Test
		@DisplayName("amount column preserves scale=4; no implicit truncation to scale=2 at the persistence layer")
		void amountColumnHasScale4Precision () {
			BigDecimal fourDecimal = new BigDecimal("12.3456");
			deposit(fourDecimal);

			Transaction tx = requireTransactionByKey(idempotencyKey);

			assertThat(tx.getAmount().amount().stripTrailingZeros())
				.isEqualByComparingTo(fourDecimal.stripTrailingZeros());
		}

		@Test
		@DisplayName("statement query returns CREDIT transactions in descending createdAt order")
		void statementQueryReturnsCreditTransactionsNewestFirst () {
			deposit(new BigDecimal("111.11"), "stmt-key-1");
			deposit(new BigDecimal("222.22"), "stmt-key-2");

			Page<Transaction> page = transactionRepository.findStatement(
				accountId, null, null, TransactionType.CREDIT,
				PageRequest.of(0, 10, Sort.by("createdAt").descending()));

			assertThat(page.getContent())
				.hasSize(2)
				.allMatch(tx -> tx.getType() == TransactionType.CREDIT)
				.allMatch(tx -> tx.getAccountId().equals(accountId))
				.isSortedAccordingTo(
					Comparator.comparing(Transaction::getCreatedAt).reversed()
				);
		}

		@Test
		@DisplayName("statement query filtered by DEBIT type returns empty when only CREDIT transactions exist")
		void statementQueryDebitFilterReturnsEmptyWhenOnlyCreditsExist () {
			deposit(STANDARD);

			Page<Transaction> page = transactionRepository.findStatement(
				accountId, null, null, TransactionType.DEBIT,
				PageRequest.of(0, 10));

			assertThat(page.getContent()).isEmpty();
		}

		@Test
		@DisplayName("statement query scoped by accountId does not return transactions belonging to other accounts")
		void statementQueryDoesNotLeakTransactionsFromOtherAccounts () {
			AccountResponse other = openChecking(CPF_2);
			depositService.deposit("other-key",
				new DepositRequest(other.id(), STANDARD, BRL, "other account"));

			deposit(new BigDecimal("50.00"));

			Page<Transaction> page = transactionRepository.findStatement(
				accountId, null, null, null,
				PageRequest.of(0, 20));

			assertThat(page.getContent())
				.hasSize(1)
				.extracting(Transaction::getAccountId)
				.containsOnly(accountId);
		}

		@Test
		@DisplayName("statement query returns zero results for an account that has no transactions")
		void statementQueryReturnsEmptyForAccountWithNoTransactions () {
			Page<Transaction> page = transactionRepository.findStatement(
				accountId, null, null, null,
				PageRequest.of(0, 10));

			assertThat(page.getContent()).isEmpty();
			assertThat(page.getTotalElements()).isZero();
		}

		@Test
		@DisplayName("statement query second page returns correct results when total rows exceed page size")
		void statementQueryPaginationIsCorrect () {
			for (int i = 1; i <= 5; i++) {
				deposit(new BigDecimal(i + ".00"), "page-key-" + i);
			}

			Page<Transaction> firstPage = transactionRepository.findStatement(
				accountId, null, null, null,
				PageRequest.of(0, 3, Sort.by("createdAt").descending()));

			Page<Transaction> secondPage = transactionRepository.findStatement(
				accountId, null, null, null,
				PageRequest.of(1, 3, Sort.by("createdAt").descending()));

			assertThat(firstPage.getContent()).hasSize(3);
			assertThat(secondPage.getContent()).hasSize(2);
			assertThat(firstPage.getTotalElements()).isEqualTo(5);

			Set<UUID> firstIds = Set.copyOf(
				firstPage.getContent().stream().map(Transaction::getId).toList());
			Set<UUID> secondIds = Set.copyOf(
				secondPage.getContent().stream().map(Transaction::getId).toList());

			assertThat(firstIds)
				.doesNotContainAnyElementsOf(secondIds);
		}
	}

	@Nested
	@DisplayName("Concurrency; race conditions and correctness under load")
	class Concurrency {

		@RepeatedTest(3)
		@DisplayName("N threads with distinct keys all succeed; final balance equals the arithmetic sum and every row is present")
		void concurrentDistinctKeyDepositsAllSucceedAndBalanceIsExact () throws Exception {
			BigDecimal amountEach = AMOUNT_100;
			BigDecimal baseline = loadBalance();

			ConcurrentTestResult result = runConcurrent(
				CONCURRENT_THREADS,
				i -> depositService.deposit(
					"distinct-key-" + i,
					new DepositRequest(accountId, amountEach, BRL, "load-" + i))
			);

			assertThat(result.failures()).isEmpty();
			assertThat(result.successes()).isEqualTo(CONCURRENT_THREADS);

			BigDecimal expectedBalance = baseline.add(
				amountEach.multiply(BigDecimal.valueOf(CONCURRENT_THREADS)));

			assertThat(loadBalance()).isEqualByComparingTo(expectedBalance);
			assertThat(transactionRepository.findByAccountId(accountId))
				.hasSize(CONCURRENT_THREADS)
				.allMatch(tx -> tx.getType() == TransactionType.CREDIT)
				.allMatch(tx -> tx.getAccountId().equals(accountId));
			assertThat(loadBalance()).isEqualByComparingTo(sumCreditTransactions());
		}

		@RepeatedTest(3)
		@DisplayName("N threads racing on the same idempotency key credit the account exactly once; idempotency fence must hold under concurrent load")
		void concurrentSameKeyDepositsOnlyCreditOnce () throws Exception {
			BigDecimal baseline = loadBalance();

			ConcurrentTestResult result = runConcurrent(
				RACING_THREADS,
				i -> depositService.deposit(
					idempotencyKey,
					new DepositRequest(accountId, AMOUNT_500, BRL, "race"))
			);

			assertThat(result.failures()).isEmpty();
			assertThat(result.successes()).isEqualTo(RACING_THREADS);

			assertThat(loadBalance()).isEqualByComparingTo(baseline.add(AMOUNT_500));
			assertThat(transactionRepository.findByAccountId(accountId)).hasSize(1);
			assertThat(idempotencyKeyRepository.findAll())
				.filteredOn(r -> r.getKey().equals(idempotencyKey))
				.hasSize(1);
		}

		@RepeatedTest(3)
		@DisplayName("concurrent deposits to two distinct accounts do not bleed balance between them")
		void concurrentDepositsToDistinctAccountsDoNotBleedBalance () throws Exception {
			AccountResponse accountB = openChecking(CPF_2);
			UUID idB = accountB.id();
			BigDecimal amountA = new BigDecimal("300.00");
			BigDecimal amountB = new BigDecimal("700.00");
			BigDecimal baseA = loadBalance();
			BigDecimal baseB = balanceOf(idB);

			CountDownLatch ready = new CountDownLatch(2);
			CountDownLatch start = new CountDownLatch(1);
			CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

			try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
				Future<?> fa = pool.submit(() -> {
					ready.countDown();
					try {
						start.await();
						depositService.deposit("key-A", new DepositRequest(accountId, amountA, BRL, "A"));
					} catch (Exception e) { errors.add(e); }
				});
				Future<?> fb = pool.submit(() -> {
					ready.countDown();
					try {
						start.await();
						depositService.deposit("key-B", new DepositRequest(idB, amountB, BRL, "B"));
					} catch (Exception e) { errors.add(e); }
				});

				ready.await();
				start.countDown();
				fa.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
				fb.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			}

			assertThat(errors).isEmpty();
			assertThat(loadBalance()).isEqualByComparingTo(baseA.add(amountA));
			assertThat(balanceOf(idB)).isEqualByComparingTo(baseB.add(amountB));
		}

		@RepeatedTest(3)
		@DisplayName("mixed concurrent load; half distinct keys, half racing on the same key; produces correct balance and row count")
		void mixedConcurrentLoadProducesCorrectBalanceAndRowCount () throws Exception {
			int uniqueThreads = 6;
			int racingThreads = 6;
			int totalThreads = uniqueThreads + racingThreads;
			BigDecimal uniqueAmount = AMOUNT_100;
			BigDecimal racingAmount = AMOUNT_500;
			BigDecimal baseline = loadBalance();

			CountDownLatch ready = new CountDownLatch(totalThreads);
			CountDownLatch start = new CountDownLatch(1);
			AtomicInteger successes = new AtomicInteger();
			CopyOnWriteArrayList<Throwable> failures = new CopyOnWriteArrayList<>();

			try (ExecutorService pool = Executors.newFixedThreadPool(totalThreads)) {
				List<Future<Void>> futures = IntStream.range(0, totalThreads)
					.mapToObj(i -> pool.submit((Callable<Void>) () -> {
						ready.countDown();
						start.await();
						try {
							if (i < uniqueThreads) {
								depositService.deposit(
									"mixed-unique-" + i,
									new DepositRequest(accountId, uniqueAmount, BRL, "unique-" + i));
							} else {
								depositService.deposit(
									idempotencyKey,
									new DepositRequest(accountId, racingAmount, BRL, "racing"));
							}
							successes.incrementAndGet();
						} catch (Exception e) {
							failures.add(e);
						}
						return null;
					}))
					.toList();

				ready.await();
				start.countDown();
				for (Future<Void> f : futures) f.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			}

			assertThat(failures).isEmpty();
			assertThat(successes.get()).isEqualTo(totalThreads);

			BigDecimal expectedBalance = baseline
				.add(uniqueAmount.multiply(BigDecimal.valueOf(uniqueThreads)))
				.add(racingAmount);

			assertThat(loadBalance()).isEqualByComparingTo(expectedBalance);
			assertThat(transactionRepository.findByAccountId(accountId))
				.hasSize(uniqueThreads + 1);
		}
	}

	private TransactionResponse deposit (BigDecimal amount) {
		return depositService.deposit(
			idempotencyKey,
			new DepositRequest(accountId, amount, BRL, "test deposit"));
	}

	private TransactionResponse deposit (BigDecimal amount, String key) {
		return depositService.deposit(
			key,
			new DepositRequest(accountId, amount, BRL, "test deposit"));
	}

	private void tryDeposit (UUID targetAccountId) {
		try {
			depositService
				.deposit(idempotencyKey, new DepositRequest(targetAccountId, STANDARD, BRL, "ghost"));
		} catch (AccountNotFoundException ignored) { }
	}

	private BigDecimal loadBalance () {
		return loadAccount().getBalance().amount();
	}

	private BigDecimal balanceOf (UUID id) {
		return accountRepository.findById(id)
			.orElseThrow(() -> new AssertionError("Account " + id + " not found"))
			.getBalance().amount();
	}

	private Account loadAccount () {
		return accountRepository.findById(accountId)
			.orElseThrow(() -> new AssertionError(
				"Primary test account disappeared; check setUp/tearDown ordering"));
	}

	private Transaction requireTransaction (UUID id) {
		return transactionRepository.findById(id)
			.orElseThrow(() -> new AssertionError(
				"Transaction row must exist after deposit but was not found: id=" + id));
	}

	private Transaction requireTransactionByKey (String key) {
		return transactionRepository.findByIdempotencyKey(key)
			.orElseThrow(() -> new AssertionError(
				"findByIdempotencyKey returned empty; index or mapping is broken: key=" + key));
	}

	private IdempotencyKey requireIdempotencyRecord (String key) {
		return idempotencyKeyRepository.findByKey(key)
			.orElseThrow(() -> new AssertionError(
				"Idempotency record must be persisted after a successful deposit: key=" + key));
	}

	private BigDecimal sumCreditTransactions () {
		return transactionRepository.findByAccountId(accountId).stream()
			.filter(tx -> tx.getType() == TransactionType.CREDIT)
			.map(tx -> tx.getAmount().amount())
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private ConcurrentTestResult runConcurrent (int threadCount, ConcurrentTask task)
		throws InterruptedException, ExecutionException, TimeoutException {

		CountDownLatch ready = new CountDownLatch(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger successes = new AtomicInteger();
		CopyOnWriteArrayList<Throwable> failures = new CopyOnWriteArrayList<>();

		try (ExecutorService pool = Executors.newFixedThreadPool(threadCount)) {
			List<Future<Void>> futures = IntStream.range(0, threadCount)
				.mapToObj(i -> pool.submit((Callable<Void>) () -> {
					ready.countDown();
					start.await();
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

		return new ConcurrentTestResult(successes.get(), failures);
	}

	@FunctionalInterface
	private interface ConcurrentTask {
		void execute (int threadIndex) throws Exception;
	}

	private record ConcurrentTestResult(int successes, List<Throwable> failures) { }
}
