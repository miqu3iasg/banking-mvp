package com.miqu3iasg.banking_mvp.transaction.service;

import com.miqu3iasg.banking.account.api.dto.AccountResponse;
import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.domain.AccountAction;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import com.miqu3iasg.banking.shared.idempotency.IdempotencyKey;
import com.miqu3iasg.banking.shared.idempotency.IdempotencyKeyStatus;
import com.miqu3iasg.banking.transaction.api.dto.DepositRequest;
import com.miqu3iasg.banking.transaction.api.dto.TransactionResponse;
import com.miqu3iasg.banking.transaction.api.dto.WithdrawalRequest;
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

class WithdrawalServiceIntegrationTest extends AbstractIntegrationTestSupport {

	private static final String BRL = "BRL";

	private static final BigDecimal FUND = new BigDecimal("1000.00");
	private static final BigDecimal STANDARD = new BigDecimal("500.00");
	private static final BigDecimal SMALL = new BigDecimal("0.01");
	private static final BigDecimal LARGE = new BigDecimal("999.9999");
	private static final BigDecimal AMOUNT_100 = new BigDecimal("100.00");
	private static final BigDecimal AMOUNT_200 = new BigDecimal("200.00");
	private static final BigDecimal AMOUNT_300 = new BigDecimal("300.00");
	private static final BigDecimal AMOUNT_500 = new BigDecimal("500.00");
	private static final BigDecimal AMOUNT_123_45 = new BigDecimal("123.45");
	private static final BigDecimal AMOUNT_1_00 = new BigDecimal("1.00");
	private static final BigDecimal AMOUNT_SUB_CENT = new BigDecimal("0.0001");

	private static final Duration IDEMPOTENCY_KEY_RETENTION = Duration.ofHours(24);
	private static final String OPERATION_TYPE_WITHDRAWAL = "WITHDRAWAL";
	private static final String KEY_PREFIX_WITHDRAWAL = "withdrawal:";

	private static final int CONCURRENT_THREADS = 12;
	private static final int RACING_THREADS = 10;
	private static final long FUTURE_TIMEOUT_SECONDS = 30;

	private UUID accountId;
	private String idempotencyKey;

	@BeforeEach
	void setUp () {
		accountId = openChecking(CPF_1).id();
		idempotencyKey = KEY_PREFIX_WITHDRAWAL + UUID.randomUUID();
		fund(FUND);  // seed the account with enough balance for all standard cases
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
			TransactionResponse response = withdraw(STANDARD);
			Instant after = Instant.now();

			Transaction tx = requireTransaction(response.transactionId());

			assertThat(tx.getId()).isNotNull();
			assertThat(tx.getType()).isEqualTo(TransactionType.DEBIT);
			assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
			assertThat(tx.getAccountId()).isEqualTo(accountId);
			assertThat(tx.getAmount().amount()).isEqualByComparingTo(STANDARD);
			assertThat(tx.getAmount().currency().getCurrencyCode()).isEqualTo(BRL);
			assertThat(tx.getIdempotencyKey()).isEqualTo(idempotencyKey);
			assertThat(tx.getCounterpartAccountId()).isNull();
			assertThat(tx.getReferenceId()).isNull();
			assertThat(tx.getCreatedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
		}

		@Test
		@DisplayName("response DTO mirrors every field of the persisted transaction row")
		void responseDtoIsDerivedFromPersistedRow () {
			TransactionResponse response = withdraw(STANDARD);
			Transaction tx = requireTransaction(response.transactionId());

			assertThat(response.transactionId()).isEqualTo(tx.getId());
			assertThat(response.accountId()).isEqualTo(tx.getAccountId());
			assertThat(response.amount()).isEqualByComparingTo(tx.getAmount().amount());
			assertThat(response.currency()).isEqualTo(tx.getAmount().currency().getCurrencyCode());
			assertThat(response.type()).isEqualTo(TransactionType.DEBIT);
			assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
		}

		@Test
		@DisplayName("description provided in the request is preserved verbatim on the transaction row")
		void descriptionIsStoredVerbatim () {
			String description = "Rent payment; March 2025";
			withdrawalService.withdraw(idempotencyKey,
				new WithdrawalRequest(accountId, STANDARD, BRL, description));

			Transaction tx = requireTransactionByKey(idempotencyKey);
			assertThat(tx.getDescription()).isEqualTo(description);
		}

		@Test
		@DisplayName("null description is tolerated and stored as null")
		void nullDescriptionIsStoredAsNull () {
			withdrawalService.withdraw(idempotencyKey,
				new WithdrawalRequest(accountId, STANDARD, BRL, null));

			Transaction tx = requireTransactionByKey(idempotencyKey);
			assertThat(tx.getDescription()).isNull();
		}

		@Test
		@DisplayName("empty-string description is stored verbatim; empty string is a valid, distinct value from null")
		void emptyDescriptionIsStoredVerbatim () {
			withdrawalService.withdraw(idempotencyKey,
				new WithdrawalRequest(accountId, STANDARD, BRL, ""));

			Transaction tx = requireTransactionByKey(idempotencyKey);
			assertThat(tx.getDescription()).isEmpty();
		}

		@Test
		@DisplayName("transaction is findable by its unique idempotency-key index")
		void transactionIsIndexedByIdempotencyKey () {
			TransactionResponse response = withdraw(STANDARD);
			Transaction byKey = requireTransactionByKey(idempotencyKey);

			assertThat(byKey.getId()).isEqualTo(response.transactionId());
			assertThat(byKey.getAccountId()).isEqualTo(accountId);
		}

		@ParameterizedTest(name = "amount = {0}")
		@ValueSource(strings = {"0.0001", "0.01", "1.00", "100.00", "999.9999"})
		@DisplayName("monetary precision is preserved end-to-end across the full representable scale")
		void monetaryPrecisionIsPreservedEndToEnd (String raw) {
			// Ensure balance covers the withdrawal amount
			String fundKey = "precision-fund:" + raw;
			depositService.deposit(fundKey,
				new DepositRequest(accountId, new BigDecimal("1000.00"), BRL, "extra fund"));

			BigDecimal amount = new BigDecimal(raw);
			String key = "precision-key:" + raw;
			BigDecimal balanceBefore = loadBalance();

			TransactionResponse response = withdrawalService.withdraw(
				key, new WithdrawalRequest(accountId, amount, BRL, "precision probe"));

			Transaction tx = requireTransaction(response.transactionId());

			assertThat(tx.getAmount().amount()).isEqualByComparingTo(amount);
			assertThat(response.amount()).isEqualByComparingTo(amount);
			assertThat(loadBalance()).isEqualByComparingTo(balanceBefore.subtract(amount));
		}
	}

	@Nested
	@DisplayName("Account balance; financial correctness")
	class AccountBalance {

		@Test
		@DisplayName("balance delta after withdrawal equals the request amount to the last cent")
		void balanceDeltaEqualsWithdrawalAmount () {
			BigDecimal before = loadBalance();
			withdraw(STANDARD);

			assertThat(before.subtract(loadBalance()))
				.isEqualByComparingTo(STANDARD);
		}

		@Test
		@DisplayName("three sequential withdrawals reduce the balance linearly; no loss or gain between operations")
		void sequentialWithdrawalsReduceBalanceWithoutDrift () {
			BigDecimal w1 = new BigDecimal("100.00");
			BigDecimal w2 = new BigDecimal("200.50");
			BigDecimal w3 = new BigDecimal("100.0001");
			BigDecimal baseline = loadBalance();

			withdraw(w1, "key-seq-1");
			withdraw(w2, "key-seq-2");
			withdraw(w3, "key-seq-3");

			assertThat(loadBalance())
				.isEqualByComparingTo(baseline.subtract(w1).subtract(w2).subtract(w3));
		}

		@Test
		@DisplayName("a withdrawal from account A never touches the balance of unrelated account B")
		void withdrawalDoesNotAffectSiblingAccountBalance () {
			AccountResponse sibling = openChecking(CPF_2);
			fund(AMOUNT_500, sibling.id(), "sibling-fund");
			BigDecimal siblingPre = balanceOf(sibling.id());

			withdraw(STANDARD);

			assertThat(balanceOf(sibling.id()))
				.isEqualByComparingTo(siblingPre);
		}

		@Test
		@DisplayName("smallest representable amount (0.0001) is fully honoured in the balance; no truncation at 2dp")
		void subCentAmountIsFullyReflectedInBalance () {
			BigDecimal baseline = loadBalance();

			withdraw(AMOUNT_SUB_CENT);

			assertThat(loadBalance())
				.isEqualByComparingTo(baseline.subtract(AMOUNT_SUB_CENT));
		}

		@Test
		@DisplayName("large high-precision withdrawal is stored without overflow or rounding in the balance column")
		void largeHighPrecisionWithdrawalStoredWithoutOverflow () {
			// Fund beyond the large amount
			depositService.deposit("overflow-fund",
				new DepositRequest(accountId, new BigDecimal("1000.00"), BRL, "extra"));
			BigDecimal baseline = loadBalance();

			withdraw(LARGE, "key-large");

			assertThat(loadBalance())
				.isEqualByComparingTo(baseline.subtract(LARGE));
		}

		@Test
		@DisplayName("withdrawal of exactly 1.00 BRL decrements a fractional baseline balance correctly")
		void withdrawalDecrementsFractionalBaseline () {
			depositService.deposit("frac-fund",
				new DepositRequest(accountId, AMOUNT_123_45, BRL, "frac fund")
			);

			BigDecimal baseline = loadBalance();

			withdraw(AMOUNT_1_00, "decrement-key");

			assertThat(loadBalance())
				.isEqualByComparingTo(baseline.subtract(AMOUNT_1_00));
		}

		@Test
		@DisplayName("withdrawing the exact available balance brings the account to exactly zero")
		void withdrawalOfFullBalanceBringsAccountToZero () {
			BigDecimal exactBalance = loadBalance();

			withdraw(exactBalance);

			assertThat(loadBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		}
	}

	@Nested
	@DisplayName("Insufficient-funds guard-rails")
	class InsufficientFunds {

		@Test
		@DisplayName("withdrawing more than the available balance throws an exception")
		void withdrawalExceedingBalanceIsRejected () {
			BigDecimal tooMuch = loadBalance().add(BigDecimal.ONE);

			assertThatThrownBy(() -> withdraw(tooMuch))
				.isInstanceOf(RuntimeException.class);
		}

		@Test
		@DisplayName("balance is unchanged when an over-limit withdrawal is rejected")
		void balanceIsUnchangedAfterRejectedWithdrawal () {
			BigDecimal before = loadBalance();
			BigDecimal tooMuch = before.add(new BigDecimal("0.01"));

			try { withdraw(tooMuch); } catch (RuntimeException ignored) { }

			assertThat(loadBalance()).isEqualByComparingTo(before);
		}

		@Test
		@DisplayName("no transaction row is written when the withdrawal exceeds available funds")
		void noTransactionRowWrittenOnInsufficientFunds () {
			BigDecimal tooMuch = loadBalance().add(BigDecimal.ONE);

			try { withdraw(tooMuch); } catch (RuntimeException ignored) { }

			assertThat(transactionRepository.findByAccountId(accountId))
				.filteredOn(tx -> tx.getType() == TransactionType.DEBIT)
				.isEmpty();
		}

		@Test
		@DisplayName("no idempotency record is written on rejection; the key remains available for a corrected retry")
		void noIdempotencyRecordWrittenOnInsufficientFunds () {
			BigDecimal tooMuch = loadBalance().add(BigDecimal.ONE);

			try { withdraw(tooMuch); } catch (RuntimeException ignored) { }

			assertThat(idempotencyKeyRepository.findByKey(idempotencyKey)).isEmpty();
		}

		@Test
		@DisplayName("client can successfully retry with the same key after correcting the amount")
		void retryWithCorrectedAmountSucceedsWithSameKey () {
			BigDecimal tooMuch = loadBalance().add(new BigDecimal("0.01"));

			try { withdraw(tooMuch); } catch (RuntimeException ignored) { }

			BigDecimal correctAmount = loadBalance();
			TransactionResponse response = withdrawalService.withdraw(
				idempotencyKey,
				new WithdrawalRequest(accountId, correctAmount, BRL, "corrected retry")
			);

			assertThat(response.transactionId()).isNotNull();
			assertThat(loadBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		}
	}

	@Nested
	@DisplayName("Idempotency; replay safety")
	class IdempotencyGuarantees {

		@Test
		@DisplayName("replaying the same key returns a response identical to the original across all fields")
		void replayReturnsCachedResponseWithIdenticalFields () {
			TransactionResponse original = withdraw(STANDARD);
			TransactionResponse replayed = withdraw(STANDARD);

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
			withdraw(STANDARD);
			withdraw(STANDARD);

			assertThat(transactionRepository.findByAccountId(accountId))
				.filteredOn(tx -> tx.getType() == TransactionType.DEBIT)
				.hasSize(1);
		}

		@Test
		@DisplayName("replaying the same key does not re-debit the account balance")
		void replayDoesNotDoubleDebit () {
			withdraw(STANDARD);
			BigDecimal balanceAfterFirst = loadBalance();

			withdraw(STANDARD);

			assertThat(loadBalance())
				.isEqualByComparingTo(balanceAfterFirst);
		}

		@Test
		@DisplayName("replay with a different amount in the payload returns the original amount; the key always wins over the payload")
		void replayWithDifferentAmountReturnsOriginalAmount () {
			withdraw(AMOUNT_100);
			BigDecimal balanceAfterOriginal = loadBalance();

			TransactionResponse replayed = withdrawalService.withdraw(
				idempotencyKey,
				new WithdrawalRequest(accountId, new BigDecimal("999.99"), BRL, "replay-tamper")
			);

			assertThat(replayed.amount()).isEqualByComparingTo(AMOUNT_100);
			assertThat(loadBalance()).isEqualByComparingTo(balanceAfterOriginal);
			assertThat(transactionRepository.findByAccountId(accountId)
				.stream().filter(tx -> tx.getType() == TransactionType.DEBIT).count())
				.isEqualTo(1);
		}

		@Test
		@DisplayName("idempotency record is COMPLETED with correct operationType, non-blank JSON body, and a 24-hour expiry")
		void idempotencyRecordIsPersistedWithCorrectMetadata () {
			Instant before = Instant.now();
			withdraw(STANDARD);
			Instant after = Instant.now();

			IdempotencyKey record = requireIdempotencyRecord(idempotencyKey);

			assertThat(record.getStatus()).isEqualTo(IdempotencyKeyStatus.COMPLETED);
			assertThat(record.getOperationType()).isEqualTo(OPERATION_TYPE_WITHDRAWAL);

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
		@DisplayName("two distinct keys on the same account produce independent DEBIT rows and the balance equals the correct net")
		void distinctKeysProduceIndependentTransactionsAndCorrectBalance () {
			BigDecimal baseline = loadBalance();

			TransactionResponse r1 = withdraw(AMOUNT_100, "key-alpha");
			TransactionResponse r2 = withdraw(AMOUNT_200, "key-beta");

			assertThat(r1.transactionId()).isNotEqualTo(r2.transactionId());

			assertThat(transactionRepository.findByAccountId(accountId))
				.filteredOn(tx -> tx.getType() == TransactionType.DEBIT)
				.hasSize(2)
				.extracting(Transaction::getId)
				.containsExactlyInAnyOrder(r1.transactionId(), r2.transactionId());

			assertThat(loadBalance())
				.isEqualByComparingTo(baseline.subtract(AMOUNT_100).subtract(AMOUNT_200));
		}

		@Test
		@DisplayName("exactly one idempotency record exists per key after first call plus multiple replays")
		void exactlyOneIdempotencyRecordExistsAfterMultipleReplays () {
			withdraw(STANDARD);
			withdraw(STANDARD);
			withdraw(STANDARD);

			assertThat(idempotencyKeyRepository.findAll())
				.filteredOn(r -> r.getKey().equals(idempotencyKey))
				.hasSize(1);
		}

		@Test
		@DisplayName("idempotency key that exists is not expired immediately after use")
		void freshIdempotencyKeyIsNotExpired () {
			withdraw(STANDARD);
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
				withdrawalService.withdraw(
					idempotencyKey,
					new WithdrawalRequest(phantom, STANDARD, BRL, "ghost")
				)
			).isInstanceOf(AccountNotFoundException.class);
		}

		@Test
		@DisplayName("no transaction row is written when the account does not exist")
		void noTransactionRowWrittenWhenAccountNotFound () {
			UUID phantom = UUID.randomUUID();

			tryWithdraw(phantom);

			assertThat(transactionRepository.findByAccountId(phantom)).isEmpty();
		}

		@Test
		@DisplayName("client can successfully retry with the same key after correcting a phantom account ID")
		void retryAfterPhantomAccountSucceedsWithSameKey () {
			tryWithdraw(UUID.randomUUID());

			BigDecimal balanceBefore = loadBalance();

			TransactionResponse response = withdrawalService.withdraw(
				idempotencyKey,
				new WithdrawalRequest(accountId, STANDARD, BRL, "corrected retry")
			);

			assertThat(response.transactionId()).isNotNull();
			assertThat(loadBalance()).isEqualByComparingTo(balanceBefore.subtract(STANDARD));
		}

		@Test
		@DisplayName("throws AccountNotFoundException when the account is deleted between request receipt and the DB lookup")
		void throwsWhenAccountDeletedBeforeProcessing () {
			accountRepository.deleteById(accountId);

			assertThatThrownBy(() -> withdraw(STANDARD))
				.isInstanceOf(AccountNotFoundException.class);
		}

		@Test
		@DisplayName("withdrawal from a BLOCKED account is rejected; balance, transaction table and idempotency table must remain untouched")
		void blockedAccountRejectsWithdrawalAndLeavesNoSideEffects () {
			withdrawalService.withdraw(
				"drain-before-close",
				new WithdrawalRequest(accountId, loadBalance(), BRL, "drain before close")
			);

			accountService.applyStatusAction(accountId, AccountAction.BLOCK_ACCOUNT_USAGE);
			BigDecimal balanceBefore = loadBalance();

			assertThatThrownBy(() -> withdraw(STANDARD))
				.isInstanceOf(RuntimeException.class);

			assertThat(loadBalance()).isEqualByComparingTo(balanceBefore);

			assertThat(transactionRepository.findByAccountId(accountId)
				.stream().filter(tx -> tx.getIdempotencyKey().equals(idempotencyKey)).toList())
				.isEmpty();

			assertThat(idempotencyKeyRepository.findByKey(idempotencyKey)).isEmpty();
		}

		@Test
		@DisplayName("withdrawal from a CLOSED account is rejected; balance, transaction table and idempotency table must remain untouched")
		void closedAccountRejectsWithdrawalAndLeavesNoSideEffects () {
			withdrawalService.withdraw(
				"drain-before-close",
				new WithdrawalRequest(accountId, loadBalance(), BRL, "drain before close")
			);

			accountService.applyStatusAction(accountId, AccountAction.CLOSE_ACCOUNT);

			BigDecimal balanceBefore = loadBalance();

			assertThatThrownBy(() -> withdraw(STANDARD))
				.isInstanceOf(RuntimeException.class);

			assertThat(loadBalance()).isEqualByComparingTo(balanceBefore);

			assertThat(transactionRepository.findByAccountId(accountId))
				.filteredOn(tx -> tx.getIdempotencyKey().equals(idempotencyKey))
				.isEmpty();

			assertThat(idempotencyKeyRepository.findByKey(idempotencyKey)).isEmpty();
		}
	}

	@Nested
	@DisplayName("Transactional atomicity; balance and ledger are always in sync")
	class TransactionalAtomicity {

		@Test
		@DisplayName("account balance equals the difference between the seeded fund and the single withdrawal amount")
		void balanceEqualsTransactionAmountAfterSingleWithdrawal () {
			BigDecimal fundedBalance = loadBalance();
			withdraw(STANDARD);

			BigDecimal expected = fundedBalance.subtract(sumDebitTransactions());
			assertThat(loadBalance()).isEqualByComparingTo(expected);
		}

		@Test
		@DisplayName("account balance is reduced by the exact sum of all DEBIT transactions after three sequential withdrawals")
		void balanceEqualsTransactionSumAfterMultipleWithdrawals () {
			BigDecimal baseline = loadBalance();
			withdraw(new BigDecimal("111.11"), "k1");
			withdraw(new BigDecimal("222.22"), "k2");
			withdraw(new BigDecimal("100.0001"), "k3");

			assertThat(loadBalance())
				.isEqualByComparingTo(baseline.subtract(sumDebitTransactions()));
		}

		@Test
		@DisplayName("currency on the account balance matches the currency on the transaction row after withdrawal")
		void accountBalanceCurrencyMatchesTransactionCurrency () {
			withdraw(STANDARD);

			Account account = loadAccount();
			Transaction tx = requireTransactionByKey(idempotencyKey);

			assertThat(account.getBalance().currency())
				.isEqualTo(tx.getAmount().currency());
		}

		@Test
		@DisplayName("withdrawal to a phantom account is fully rolled back; the legitimate account balance and ledger are unaffected")
		void withdrawalToPhantomAccountIsFullyRolledBack () {
			BigDecimal balanceBefore = loadBalance();

			try {
				withdrawalService.withdraw(
					"rollback-key",
					new WithdrawalRequest(UUID.randomUUID(), STANDARD, BRL, "phantom")
				);
			} catch (AccountNotFoundException ignored) { }

			assertThat(loadBalance()).isEqualByComparingTo(balanceBefore);
			assertThat(transactionRepository.findByAccountId(accountId)
				.stream().filter(tx -> tx.getType() == TransactionType.DEBIT).toList())
				.isEmpty();
		}

		@Test
		@DisplayName("balance equals ledger invariant holds after mixed-precision sequential withdrawals")
		void balanceEqualsLedgerSumAfterMixedPrecisionWithdrawals () {
			BigDecimal baseline = loadBalance();

			withdraw(new BigDecimal("0.0001"), "mp-k1");
			withdraw(new BigDecimal("1.50"), "mp-k2");
			withdraw(new BigDecimal("100.00"), "mp-k3");

			assertThat(loadBalance())
				.isEqualByComparingTo(baseline.subtract(sumDebitTransactions()));
		}
	}

	@Nested
	@DisplayName("Database-level integrity invariants")
	class DatabaseIntegrity {

		@Test
		@DisplayName("unique constraint on idempotency_key in the transactions table prevents duplicate rows at the DB layer")
		void uniqueConstraintOnIdempotencyKeyIsEnforced () {
			withdraw(STANDARD);

			assertThat(transactionRepository.findAll())
				.filteredOn(tx -> idempotencyKey.equals(tx.getIdempotencyKey()))
				.hasSize(1);
		}

		@Test
		@DisplayName("account_id on the transaction row is non-null and correctly bound to the request account")
		void transactionAccountIdIsNonNullAndCorrect () {
			withdraw(STANDARD);

			Transaction tx = requireTransactionByKey(idempotencyKey);

			assertThat(tx.getAccountId())
				.isNotNull()
				.isEqualTo(accountId);
		}

		@Test
		@DisplayName("amount column preserves scale=4; no implicit truncation to scale=2 at the persistence layer")
		void amountColumnHasScale4Precision () {
			BigDecimal fourDecimal = new BigDecimal("12.3456");
			withdraw(fourDecimal);

			Transaction tx = requireTransactionByKey(idempotencyKey);

			assertThat(tx.getAmount().amount().stripTrailingZeros())
				.isEqualByComparingTo(fourDecimal.stripTrailingZeros());
		}

		@Test
		@DisplayName("statement query returns DEBIT transactions in descending createdAt order")
		void statementQueryReturnsDebitTransactionsNewestFirst () {
			withdraw(new BigDecimal("111.11"), "stmt-key-1");
			withdraw(new BigDecimal("222.22"), "stmt-key-2");

			Page<Transaction> page = transactionRepository.findStatement(
				accountId, null, null, TransactionType.DEBIT,
				PageRequest.of(0, 10, Sort.by("createdAt").descending())
			);

			assertThat(page.getContent())
				.hasSize(2)
				.allMatch(tx -> tx.getType() == TransactionType.DEBIT)
				.allMatch(tx -> tx.getAccountId().equals(accountId))
				.isSortedAccordingTo(
					Comparator.comparing(Transaction::getCreatedAt).reversed()
				);
		}

		@Test
		@DisplayName("statement query filtered by CREDIT type returns empty when only DEBIT transactions exist")
		void statementQueryCreditFilterReturnsEmptyWhenOnlyDebitsExist () {
			withdraw(STANDARD);

			Page<Transaction> page = transactionRepository.findStatement(
				accountId, null, null, TransactionType.CREDIT,
				PageRequest.of(0, 10)
			);

			UUID freshId = openChecking(CPF_2).id();
			depositService.deposit("credit-seed", new DepositRequest(freshId, AMOUNT_100, BRL, "seed"));

			withdrawalService.withdraw(
				"debit-only",
				new WithdrawalRequest(freshId, AMOUNT_100, BRL, "drain")
			);

			Page<Transaction> freshPage = transactionRepository.findStatement(
				freshId, null, null, TransactionType.DEBIT,
				PageRequest.of(0, 10));

			assertThat(freshPage.getContent())
				.allMatch(tx -> tx.getType() == TransactionType.DEBIT);
		}

		@Test
		@DisplayName("statement query scoped by accountId does not return transactions belonging to other accounts")
		void statementQueryDoesNotLeakTransactionsFromOtherAccounts () {
			AccountResponse other = openChecking(CPF_2);

			depositService.deposit("other-fund",
				new DepositRequest(other.id(), AMOUNT_500, BRL, "other fund")
			);

			withdrawalService.withdraw(
				"other-withdrawal",
				new WithdrawalRequest(other.id(), AMOUNT_100, BRL, "other withdrawal")
			);

			withdraw(AMOUNT_100);

			Page<Transaction> page = transactionRepository.findStatement(
				accountId, null, null, TransactionType.DEBIT,
				PageRequest.of(0, 20)
			);

			assertThat(page.getContent())
				.allMatch(tx -> tx.getAccountId().equals(accountId));
		}

		@Test
		@DisplayName("statement query returns zero DEBIT results for an account that has had no withdrawals")
		void statementQueryReturnsEmptyForAccountWithNoWithdrawals () {
			UUID freshId = openChecking(CPF_2).id();

			Page<Transaction> page = transactionRepository.findStatement(
				freshId, null, null, TransactionType.DEBIT,
				PageRequest.of(0, 10)
			);

			assertThat(page.getContent()).isEmpty();
			assertThat(page.getTotalElements()).isZero();
		}

		@Test
		@DisplayName("statement query second page returns correct results when total rows exceed page size")
		void statementQueryPaginationIsCorrect () {
			for (int i = 1; i <= 5; i++) {
				withdraw(new BigDecimal(i + ".00"), "page-key-" + i);
			}

			Page<Transaction> firstPage = transactionRepository.findStatement(
				accountId, null, null, TransactionType.DEBIT,
				PageRequest.of(0, 3, Sort.by("createdAt").descending())
			);

			Page<Transaction> secondPage = transactionRepository.findStatement(
				accountId, null, null, TransactionType.DEBIT,
				PageRequest.of(1, 3, Sort.by("createdAt").descending())
			);

			assertThat(firstPage.getContent()).hasSize(3);
			assertThat(secondPage.getContent()).hasSize(2);
			assertThat(firstPage.getTotalElements()).isEqualTo(5);

			Set<UUID> firstIds = Set.copyOf(
				firstPage
					.getContent()
					.stream().map(Transaction::getId)
					.toList()
			);

			Set<UUID> secondIds = Set.copyOf(
				secondPage
					.getContent()
					.stream().map(Transaction::getId)
					.toList()
			);

			assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
		}
	}

	@Nested
	@DisplayName("Concurrency; race conditions and correctness under load")
	class Concurrency {

		@RepeatedTest(3)
		@DisplayName("N threads with distinct keys all succeed; final balance equals arithmetic difference and every row is present")
		void concurrentDistinctKeyWithdrawalsAllSucceedAndBalanceIsExact () throws Exception {
			BigDecimal amountEach = AMOUNT_100;

			depositService.deposit(
				"pre-fund-concurrent",
				new DepositRequest(
					accountId,
					amountEach.multiply(BigDecimal.valueOf(CONCURRENT_THREADS)),
					BRL,
					"pre-fund"
				)
			);

			BigDecimal baseline = loadBalance();

			ConcurrentTestResult result = runConcurrent(
				CONCURRENT_THREADS,
				i -> withdrawalService.withdraw(
					"distinct-key-" + i,
					new WithdrawalRequest(accountId, amountEach, BRL, "load-" + i))
			);

			assertThat(result.failures()).isEmpty();
			assertThat(result.successes()).isEqualTo(CONCURRENT_THREADS);

			BigDecimal expectedBalance = baseline
				.subtract(amountEach.multiply(BigDecimal.valueOf(CONCURRENT_THREADS)));

			assertThat(loadBalance()).isEqualByComparingTo(expectedBalance);

			assertThat(transactionRepository.findByAccountId(accountId))
				.filteredOn(tx -> tx.getType() == TransactionType.DEBIT)
				.hasSize(CONCURRENT_THREADS)
				.allMatch(tx -> tx.getAccountId().equals(accountId));
		}

		@RepeatedTest(3)
		@DisplayName("N threads racing on the same idempotency key debit the account exactly once; idempotency fence must hold under concurrent load")
		void concurrentSameKeyWithdrawalsOnlyDebitOnce () throws Exception {
			BigDecimal baseline = loadBalance();

			ConcurrentTestResult result = runConcurrent(
				RACING_THREADS,
				i -> withdrawalService.withdraw(
					idempotencyKey,
					new WithdrawalRequest(accountId, AMOUNT_100, BRL, "race")
				)
			);

			assertThat(result.failures()).isEmpty();
			assertThat(result.successes()).isEqualTo(RACING_THREADS);

			assertThat(loadBalance()).isEqualByComparingTo(baseline.subtract(AMOUNT_100));
			assertThat(transactionRepository.findByAccountId(accountId))
				.filteredOn(tx -> tx.getType() == TransactionType.DEBIT)
				.hasSize(1);

			assertThat(idempotencyKeyRepository.findAll())
				.filteredOn(r -> r.getKey().equals(idempotencyKey))
				.hasSize(1);
		}

		@RepeatedTest(3)
		@DisplayName("concurrent withdrawals to two distinct accounts do not bleed balance between them")
		void concurrentWithdrawalsToDistinctAccountsDoNotBleedBalance () throws Exception {
			AccountResponse accountB = openChecking(CPF_2);
			UUID idB = accountB.id();
			BigDecimal amountA = new BigDecimal("300.00");
			BigDecimal amountB = new BigDecimal("200.00");

			depositService.deposit("fund-B",
				new DepositRequest(idB, AMOUNT_500, BRL, "fund B")
			);

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

						withdrawalService.withdraw(
							"key-A",
							new WithdrawalRequest(accountId, amountA, BRL, "A")
						);
					} catch (Exception e) { errors.add(e); }
				});

				Future<?> fb = pool.submit(() -> {
					ready.countDown();

					try {
						start.await();

						withdrawalService.withdraw(
							"key-B",
							new WithdrawalRequest(idB, amountB, BRL, "B")
						);
					} catch (Exception e) { errors.add(e); }
				});

				ready.await();
				start.countDown();

				fa.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
				fb.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			}

			assertThat(errors).isEmpty();
			assertThat(loadBalance()).isEqualByComparingTo(baseA.subtract(amountA));
			assertThat(balanceOf(idB)).isEqualByComparingTo(baseB.subtract(amountB));
		}

		@RepeatedTest(3)
		@DisplayName("mixed concurrent load; half distinct keys, half racing on the same key; produces correct balance and row count")
		void mixedConcurrentLoadProducesCorrectBalanceAndRowCount () throws Exception {
			int uniqueThreads = 6;
			int racingThreads = 6;
			int totalThreads = uniqueThreads + racingThreads;

			BigDecimal uniqueAmount = AMOUNT_100;
			BigDecimal racingAmount = AMOUNT_100;

			depositService.deposit(
				"mixed-pre-fund",
				new DepositRequest(
					accountId,
					uniqueAmount.multiply(BigDecimal.valueOf(uniqueThreads)).add(racingAmount),
					BRL,
					"mixed pre-fund"
				)
			);

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
								withdrawalService.withdraw(
									"mixed-unique-" + i,
									new WithdrawalRequest(accountId, uniqueAmount, BRL, "unique-" + i)
								);
							} else {
								withdrawalService.withdraw(
									idempotencyKey,
									new WithdrawalRequest(accountId, racingAmount, BRL, "racing")
								);
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
				.subtract(uniqueAmount.multiply(BigDecimal.valueOf(uniqueThreads)))
				.subtract(racingAmount);  // racing threads share a single debit

			assertThat(loadBalance()).isEqualByComparingTo(expectedBalance);
			assertThat(transactionRepository.findByAccountId(accountId))
				.filteredOn(tx -> tx.getType() == TransactionType.DEBIT)
				.hasSize(uniqueThreads + 1);
		}
	}

	private TransactionResponse withdraw (BigDecimal amount) {
		return withdrawalService.withdraw(
			idempotencyKey,
			new WithdrawalRequest(accountId, amount, BRL, "test withdrawal")
		);
	}

	private TransactionResponse withdraw (BigDecimal amount, String key) {
		return withdrawalService.withdraw(
			key,
			new WithdrawalRequest(accountId, amount, BRL, "test withdrawal")
		);
	}

	private void tryWithdraw (UUID targetAccountId) {
		try {
			withdrawalService.withdraw(
				idempotencyKey,
				new WithdrawalRequest(targetAccountId, STANDARD, BRL, "ghost")
			);
		} catch (AccountNotFoundException ignored) { }
	}

	private void fund (BigDecimal amount) {
		depositService.deposit(
			"fund:" + UUID.randomUUID(),
			new DepositRequest(accountId, amount, BRL, "initial fund")
		);
	}

	private void fund (BigDecimal amount, UUID targetId, String key) {
		depositService.deposit(
			key,
			new DepositRequest(targetId, amount, BRL, "fund")
		);
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
				"Transaction row must exist after withdrawal but was not found: id=" + id));
	}

	private Transaction requireTransactionByKey (String key) {
		return transactionRepository.findByIdempotencyKey(key)
			.orElseThrow(() -> new AssertionError(
				"findByIdempotencyKey returned empty; index or mapping is broken: key=" + key));
	}

	private IdempotencyKey requireIdempotencyRecord (String key) {
		return idempotencyKeyRepository.findByKey(key)
			.orElseThrow(() -> new AssertionError(
				"Idempotency record must be persisted after a successful withdrawal: key=" + key));
	}

	private BigDecimal sumDebitTransactions () {
		return transactionRepository.findByAccountId(accountId).stream()
			.filter(tx -> tx.getType() == TransactionType.DEBIT)
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
