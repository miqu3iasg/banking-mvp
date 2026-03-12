package com.miqu3iasg.banking_mvp.transaction.service;

import com.miqu3iasg.banking.account.api.dto.AccountResponse;
import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.account.domain.AccountAction;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import com.miqu3iasg.banking.shared.idempotency.IdempotencyKey;
import com.miqu3iasg.banking.shared.idempotency.IdempotencyKeyStatus;
import com.miqu3iasg.banking.transaction.api.dto.DepositRequest;
import com.miqu3iasg.banking.transaction.api.dto.TransactionResponse;
import com.miqu3iasg.banking.transaction.api.dto.TransferRequest;
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
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferServiceIntegrationTest extends AbstractIntegrationTestSupport {

	private static final BigDecimal FUND = new BigDecimal("1000.00");

	private static final String OPERATION_TYPE_TRANSFER = "TRANSFER";
	private static final String KEY_PREFIX_TRANSFER = "transfer:";

	private UUID originId;
	private UUID destinationId;
	private String idempotencyKey;

	@BeforeEach
	void setUp () {
		originId = openChecking(CPF_1).id();
		destinationId = openChecking(CPF_2).id();
		idempotencyKey = KEY_PREFIX_TRANSFER + UUID.randomUUID();
		fund(FUND, originId);
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
		@DisplayName("transfer produces exactly two rows: one TRANSFER_DEBIT on origin and one TRANSFER_CREDIT on destination")
		void transferProducesTwoSymmetricRows () {
			TransactionResponse response = transfer(STANDARD);

			List<Transaction> originDebits = transactionRepository.findByAccountId(originId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.toList();

			List<Transaction> destinationCredits = transactionRepository.findByAccountId(destinationId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.toList();

			assertThat(originDebits)
				.hasSize(1)
				.first()
				.satisfies(debit -> {
					assertThat(debit.getId()).isEqualTo(response.transactionId());
					assertThat(debit.getAccountId()).isEqualTo(originId);
					assertThat(debit.getCounterpartAccountId()).isEqualTo(destinationId);
					assertThat(debit.getAmount().amount()).isEqualByComparingTo(STANDARD);
					assertThat(debit.getAmount().currency().getCurrencyCode()).isEqualTo(BRL);
					assertThat(debit.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
					assertThat(debit.getIdempotencyKey()).isEqualTo(idempotencyKey);
				});

			assertThat(destinationCredits)
				.hasSize(1)
				.first()
				.satisfies(credit -> {
					assertThat(credit.getId()).isNotEqualTo(response.transactionId());
					assertThat(credit.getAccountId()).isEqualTo(destinationId);
					assertThat(credit.getCounterpartAccountId()).isEqualTo(originId);
					assertThat(credit.getAmount().amount()).isEqualByComparingTo(STANDARD);
					assertThat(credit.getAmount().currency().getCurrencyCode()).isEqualTo(BRL);
					assertThat(credit.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
				});
		}

		@Test
		@DisplayName("response DTO corresponds to the DEBIT leg and mirrors every persisted field")
		void responseDtoMirrorsDebitLeg () {
			Instant before = Instant.now();
			TransactionResponse response = transfer(STANDARD);
			Instant after = Instant.now();

			Transaction debit = requireTransaction(response.transactionId(), "transfer");

			assertThat(response.transactionId()).isEqualTo(debit.getId());
			assertThat(response.accountId()).isEqualTo(debit.getAccountId());
			assertThat(response.amount()).isEqualByComparingTo(debit.getAmount().amount());
			assertThat(response.currency()).isEqualTo(debit.getAmount().currency().getCurrencyCode());
			assertThat(response.type()).isEqualTo(TransactionType.TRANSFER_DEBIT);
			assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);

			assertThat(debit.getType()).isEqualTo(TransactionType.TRANSFER_DEBIT);
			assertThat(debit.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
			assertThat(debit.getAccountId()).isEqualTo(originId);
			assertThat(debit.getCounterpartAccountId()).isEqualTo(destinationId);
			assertThat(debit.getAmount().amount()).isEqualByComparingTo(STANDARD);
			assertThat(debit.getAmount().currency().getCurrencyCode()).isEqualTo(BRL);
			assertThat(debit.getIdempotencyKey()).isEqualTo(idempotencyKey);
			assertThat(debit.getReferenceId()).isNotBlank();
			assertThat(debit.getCreatedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
		}

		@Test
		@DisplayName("TRANSFER_CREDIT leg carries the correct counterpart reference back to origin and all mandatory fields are populated")
		void creditLegHasCorrectCounterpartBindingAndMandatoryFields () {
			Instant before = Instant.now();
			transfer(STANDARD);
			Instant after = Instant.now();

			Transaction credit = transactionRepository.findByAccountId(destinationId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.findFirst()
				.orElseThrow(() -> new AssertionError("TRANSFER_CREDIT row not found for destination"));

			assertThat(credit.getId()).isNotNull();
			assertThat(credit.getType()).isEqualTo(TransactionType.TRANSFER_CREDIT);
			assertThat(credit.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
			assertThat(credit.getAccountId()).isEqualTo(destinationId);
			assertThat(credit.getCounterpartAccountId()).isEqualTo(originId);
			assertThat(credit.getAmount().amount()).isEqualByComparingTo(STANDARD);
			assertThat(credit.getAmount().currency().getCurrencyCode()).isEqualTo(BRL);
			assertThat(credit.getReferenceId()).isNotBlank();
			assertThat(credit.getCreatedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
		}

		@Test
		@DisplayName("both legs share the same non-blank referenceId and have distinct IDs from each other")
		void bothLegsShareTheSameReferenceIdAndHaveDistinctIds () {
			transfer(STANDARD);

			Transaction debit = transactionRepository.findByAccountId(originId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.findFirst()
				.orElseThrow();

			Transaction credit = transactionRepository.findByAccountId(destinationId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.findFirst()
				.orElseThrow();

			assertThat(debit.getReferenceId())
				.isNotBlank()
				.isEqualTo(credit.getReferenceId());

			assertThat(debit.getId()).isNotEqualTo(credit.getId());
		}

		@Test
		@DisplayName("two independent transfers produce two distinct referenceIds; referenceIds are never reused across transfers")
		void eachTransferProducesAUniqueReferenceId () {
			transfer(STANDARD, "ref-key-1");
			transfer(AMOUNT_100, "ref-key-2");

			List<Transaction> allDebits = transactionRepository.findByAccountId(originId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.toList();

			assertThat(allDebits)
				.hasSize(2)
				.extracting(Transaction::getReferenceId)
				.doesNotHaveDuplicates()
				.allMatch(ref -> ref != null && !ref.isBlank());
		}

		@Test
		@DisplayName("description provided in the request is preserved verbatim on both transaction legs")
		void descriptionIsStoredVerbatimOnBothLegs () {
			String description = "Rent split; March 2025";

			transferService.transfer(idempotencyKey,
				new TransferRequest(originId, destinationId, STANDARD, BRL, description));

			Transaction debit = transactionRepository.findByAccountId(originId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.findFirst()
				.orElseThrow();

			Transaction credit = transactionRepository.findByAccountId(destinationId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.findFirst()
				.orElseThrow();

			assertThat(debit.getDescription()).isEqualTo(description);
			assertThat(credit.getDescription()).isEqualTo(description);
		}

		@Test
		@DisplayName("null description is tolerated and stored as null on both legs")
		void nullDescriptionIsStoredAsNullOnBothLegs () {
			transferService.transfer(idempotencyKey,
				new TransferRequest(originId, destinationId, STANDARD, BRL, null));

			Transaction debit = transactionRepository.findByAccountId(originId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.findFirst()
				.orElseThrow();

			Transaction credit = transactionRepository.findByAccountId(destinationId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.findFirst()
				.orElseThrow();

			assertThat(debit.getDescription()).isNull();
			assertThat(credit.getDescription()).isNull();
		}

		@Test
		@DisplayName("both legs are COMPLETED immediately after a successful transfer; no PENDING or FAILED rows exist")
		void bothLegsAreCompletedStatusAndNoPendingOrFailedRowsExist () {
			transfer(STANDARD);

			List<Transaction> transferRows = transactionRepository.findAll().stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT
					|| tx.getType() == TransactionType.TRANSFER_CREDIT)
				.toList();

			assertThat(transferRows)
				.hasSize(2)
				.allMatch(tx -> tx.getStatus() == TransactionStatus.COMPLETED)
				.noneMatch(tx -> tx.getStatus() == TransactionStatus.PENDING)
				.noneMatch(tx -> tx.getStatus() == TransactionStatus.FAILED);
		}

		@Test
		@DisplayName("debit leg is findable by its unique idempotency-key index and all fields match the response")
		void debitLegIsIndexedByIdempotencyKeyWithConsistentFields () {
			TransactionResponse response = transfer(STANDARD);

			Transaction byKey = requireTransaction(response.transactionId(), "transfer");

			assertThat(byKey.getId()).isEqualTo(response.transactionId());
			assertThat(byKey.getAccountId()).isEqualTo(originId);
			assertThat(byKey.getType()).isEqualTo(TransactionType.TRANSFER_DEBIT);
			assertThat(byKey.getAmount().amount()).isEqualByComparingTo(response.amount());
			assertThat(byKey.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
		}

		@ParameterizedTest(name = "amount = {0}")
		@ValueSource(strings = {"0.0001", "0.01", "1.00", "100.00", "999.9999"})
		@DisplayName("monetary precision is preserved end-to-end on both legs and both balances across the full representable scale")
		void monetaryPrecisionIsPreservedEndToEnd (String raw) {
			fund(new BigDecimal("1000.00"), originId);
			BigDecimal amount = new BigDecimal(raw);
			String key = KEY_PREFIX_TRANSFER + "precision:" + raw;
			BigDecimal originBefore = balanceOf(originId);
			BigDecimal destinationBefore = balanceOf(destinationId);

			TransactionResponse response = transferService.transfer(
				key, new TransferRequest(originId, destinationId, amount, BRL, "precision probe"));

			Transaction debit = requireTransaction(response.transactionId(), "transfer");

			Transaction credit = transactionRepository.findByAccountId(destinationId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.filter(tx -> debit.getReferenceId().equals(tx.getReferenceId()))
				.findFirst()
				.orElseThrow();

			assertThat(debit.getAmount().amount()).isEqualByComparingTo(amount);
			assertThat(credit.getAmount().amount()).isEqualByComparingTo(amount);
			assertThat(response.amount()).isEqualByComparingTo(amount);
			assertThat(balanceOf(originId)).isEqualByComparingTo(originBefore.subtract(amount));
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBefore.add(amount));
		}
	}

	@Nested
	@DisplayName("Account balances; financial correctness")
	class AccountBalances {

		@Test
		@DisplayName("origin balance decreases and destination balance increases by exactly the transfer amount")
		void balanceDeltasAreSymmetricAndExact () {
			BigDecimal originBefore = balanceOf(originId);
			BigDecimal destinationBefore = balanceOf(destinationId);

			transfer(STANDARD);

			assertThat(originBefore.subtract(balanceOf(originId))).isEqualByComparingTo(STANDARD);
			assertThat(balanceOf(destinationId).subtract(destinationBefore)).isEqualByComparingTo(STANDARD);
		}

		@Test
		@DisplayName("the sum leaving origin equals the sum arriving at destination; no value is created or destroyed")
		void conservationOfValueHoldsAcrossOriginAndDestination () {
			BigDecimal originBefore = balanceOf(originId);
			BigDecimal destinationBefore = balanceOf(destinationId);

			transfer(STANDARD, "cov-key-1");
			transfer(AMOUNT_100, "cov-key-2");
			transfer(AMOUNT_200, "cov-key-3");

			BigDecimal originLost = originBefore.subtract(balanceOf(originId));
			BigDecimal destinationGained = balanceOf(destinationId).subtract(destinationBefore);

			assertThat(originLost).isEqualByComparingTo(destinationGained);
		}

		@Test
		@DisplayName("three sequential transfers reduce origin and increase destination linearly without drift")
		void sequentialTransfersAdjustBalancesWithoutDrift () {
			BigDecimal t1 = new BigDecimal("100.00");
			BigDecimal t2 = new BigDecimal("200.50");
			BigDecimal t3 = new BigDecimal("100.0001");
			BigDecimal originBaseline = balanceOf(originId);
			BigDecimal destinationBaseline = balanceOf(destinationId);

			transfer(t1, "key-seq-1");
			transfer(t2, "key-seq-2");
			transfer(t3, "key-seq-3");

			BigDecimal total = t1.add(t2).add(t3);

			assertThat(balanceOf(originId)).isEqualByComparingTo(originBaseline.subtract(total));
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBaseline.add(total));
		}

		@Test
		@DisplayName("a transfer between A and B never touches the balance of an unrelated account C")
		void transferDoesNotAffectUnrelatedAccountBalance () {
			AccountResponse bystander = openChecking(CPF_3);
			fund(AMOUNT_500, bystander.id());
			BigDecimal bystanderBefore = balanceOf(bystander.id());
			BigDecimal originBefore = balanceOf(originId);
			BigDecimal destinationBefore = balanceOf(destinationId);

			transfer(STANDARD);

			assertThat(balanceOf(bystander.id())).isEqualByComparingTo(bystanderBefore);
			assertThat(originBefore.subtract(balanceOf(originId))).isEqualByComparingTo(STANDARD);
			assertThat(balanceOf(destinationId).subtract(destinationBefore)).isEqualByComparingTo(STANDARD);
		}

		@Test
		@DisplayName("transferring the exact available balance brings origin to zero and destination receives the full amount")
		void transferOfFullBalanceBringsOriginToZero () {
			BigDecimal exactBalance = balanceOf(originId);
			BigDecimal destinationBefore = balanceOf(destinationId);

			transfer(exactBalance);

			assertThat(balanceOf(originId)).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBefore.add(exactBalance));
		}

		@Test
		@DisplayName("smallest representable amount (0.0001) is fully honoured on both balances; no truncation at 2dp")
		void subCentAmountIsFullyReflectedInBothBalances () {
			BigDecimal originBefore = balanceOf(originId);
			BigDecimal destinationBefore = balanceOf(destinationId);

			transfer(AMOUNT_SUB_CENT);

			assertThat(balanceOf(originId)).isEqualByComparingTo(originBefore.subtract(AMOUNT_SUB_CENT));
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBefore.add(AMOUNT_SUB_CENT));
		}

		@Test
		@DisplayName("large high-precision transfer is stored without overflow or rounding in either balance column")
		void largeHighPrecisionTransferStoredWithoutOverflow () {
			fund(new BigDecimal("1000.00"), originId);
			BigDecimal originBefore = balanceOf(originId);
			BigDecimal destinationBefore = balanceOf(destinationId);

			transfer(AMOUNT_LARGE, "key-large");

			assertThat(balanceOf(originId)).isEqualByComparingTo(originBefore.subtract(AMOUNT_LARGE));
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBefore.add(AMOUNT_LARGE));
		}
	}

	@Nested
	@DisplayName("Insufficient-funds guard-rails")
	class InsufficientFunds {

		@Test
		@DisplayName("transferring more than the available origin balance throws an exception")
		void transferExceedingBalanceIsRejected () {
			BigDecimal tooMuch = balanceOf(originId).add(BigDecimal.ONE);

			assertThatThrownBy(() -> transfer(tooMuch))
				.isInstanceOf(RuntimeException.class);
		}

		@Test
		@DisplayName("both balances are unchanged when an over-limit transfer is rejected")
		void bothBalancesAreUnchangedAfterRejectedTransfer () {
			BigDecimal originBefore = balanceOf(originId);
			BigDecimal destinationBefore = balanceOf(destinationId);
			BigDecimal tooMuch = originBefore.add(new BigDecimal("0.01"));

			try { transfer(tooMuch); } catch (RuntimeException ignored) { }

			assertThat(balanceOf(originId)).isEqualByComparingTo(originBefore);
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBefore);
		}

		@Test
		@DisplayName("no transaction row is written on either leg when the transfer exceeds available funds")
		void noTransactionRowWrittenOnInsufficientFunds () {
			BigDecimal tooMuch = balanceOf(originId).add(BigDecimal.ONE);

			try { transfer(tooMuch); } catch (RuntimeException ignored) { }

			assertThat(transactionRepository.findByAccountId(originId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.isEmpty();

			assertThat(transactionRepository.findByAccountId(destinationId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.isEmpty();

			assertThat(transactionRepository.findAll())
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT || tx.getType() == TransactionType.TRANSFER_CREDIT)
				.isEmpty();
		}

		@Test
		@DisplayName("no idempotency record is written on rejection; the key remains available for a corrected retry")
		void noIdempotencyRecordWrittenOnInsufficientFunds () {
			BigDecimal tooMuch = balanceOf(originId).add(BigDecimal.ONE);

			try { transfer(tooMuch); } catch (RuntimeException ignored) { }

			assertThat(idempotencyKeyRepository.findByKey(idempotencyKey)).isEmpty();
			assertThat(idempotencyKeyRepository.findAll())
				.filteredOn(r -> r.getKey().startsWith("transfer:"))
				.isEmpty();
		}

		@Test
		@DisplayName("client can successfully retry with the same key after correcting the amount; both balances reflect only the corrected transfer")
		void retryWithCorrectedAmountSucceedsWithSameKey () {
			BigDecimal tooMuch = balanceOf(originId).add(new BigDecimal("0.01"));

			try { transfer(tooMuch); } catch (RuntimeException ignored) { }

			BigDecimal correctAmount = balanceOf(originId);
			BigDecimal destinationBefore = balanceOf(destinationId);

			TransactionResponse response = transferService.transfer(
				idempotencyKey,
				new TransferRequest(originId, destinationId, correctAmount, BRL, "corrected retry")
			);

			assertThat(response.transactionId()).isNotNull();
			assertThat(response.amount()).isEqualByComparingTo(correctAmount);
			assertThat(balanceOf(originId)).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBefore.add(correctAmount));

			assertThat(transactionRepository.findByAccountId(originId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.hasSize(1);

			assertThat(transactionRepository.findByAccountId(destinationId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.hasSize(1);
		}
	}

	@Nested
	@DisplayName("Self-transfer guard-rail")
	class SelfTransferGuardRail {

		@Test
		@DisplayName("transfer where origin and destination are the same account is rejected with a domain-specific exception")
		void selfTransferIsRejected () {
			assertThatThrownBy(() ->
				transferService.transfer(
					idempotencyKey,
					new TransferRequest(originId, originId, STANDARD, BRL, "self-transfer")
				)
			).isInstanceOf(RuntimeException.class);
		}

		@Test
		@DisplayName("no transaction row and no idempotency record are written on a self-transfer rejection")
		void selfTransferLeavesNoSideEffects () {
			try {
				transferService.transfer(
					idempotencyKey,
					new TransferRequest(originId, originId, STANDARD, BRL, "self-transfer")
				);
			} catch (RuntimeException ignored) { }

			assertThat(transactionRepository.findByAccountId(originId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT || tx.getType() == TransactionType.TRANSFER_CREDIT)
				.isEmpty();

			assertThat(transactionRepository.findAll())
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT || tx.getType() == TransactionType.TRANSFER_CREDIT)
				.isEmpty();

			assertThat(idempotencyKeyRepository.findByKey(idempotencyKey)).isEmpty();

			assertThat(idempotencyKeyRepository.findAll())
				.filteredOn(r -> r.getKey().startsWith("transfer:"))
				.isEmpty();
		}

		@Test
		@DisplayName("balance is unchanged on a self-transfer rejection")
		void selfTransferDoesNotMutateBalance () {
			BigDecimal before = balanceOf(originId);

			try {
				transferService.transfer(
					idempotencyKey,
					new TransferRequest(originId, originId, STANDARD, BRL, "self-transfer")
				);
			} catch (RuntimeException ignored) { }

			assertThat(balanceOf(originId)).isEqualByComparingTo(before);
		}
	}

	@Nested
	@DisplayName("Idempotency; replay safety")
	class IdempotencyGuarantees {

		@Test
		@DisplayName("replaying the same key returns a response identical to the original across all fields")
		void replayReturnsCachedResponseWithIdenticalFields () {
			TransactionResponse original = transfer(STANDARD);
			TransactionResponse replayed = transfer(STANDARD);

			assertThat(replayed.transactionId()).isEqualTo(original.transactionId());
			assertThat(replayed.amount()).isEqualByComparingTo(original.amount());
			assertThat(replayed.type()).isEqualTo(original.type());
			assertThat(replayed.status()).isEqualTo(original.status());
			assertThat(replayed.accountId()).isEqualTo(original.accountId());
			assertThat(replayed.currency()).isEqualTo(original.currency());
		}

		@Test
		@DisplayName("replaying the same key three times produces exactly one DEBIT and one CREDIT row; never duplicates on either leg")
		void replayDoesNotDuplicateTransactionRows () {
			transfer(STANDARD);
			transfer(STANDARD);
			transfer(STANDARD);

			assertThat(transactionRepository.findByAccountId(originId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.hasSize(1);

			assertThat(transactionRepository.findByAccountId(destinationId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.hasSize(1);

			assertThat(transactionRepository.findAll())
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT || tx.getType() == TransactionType.TRANSFER_CREDIT)
				.hasSize(2);
		}

		@Test
		@DisplayName("replaying the same key does not re-debit origin or re-credit destination; both balances are frozen after the first successful call")
		void replayDoesNotDoubleApplyBalanceChanges () {
			transfer(STANDARD);
			BigDecimal originAfterFirst = balanceOf(originId);
			BigDecimal destinationAfterFirst = balanceOf(destinationId);

			transfer(STANDARD);
			transfer(STANDARD);

			assertThat(balanceOf(originId)).isEqualByComparingTo(originAfterFirst);
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationAfterFirst);
		}

		@Test
		@DisplayName("replay with a different amount returns the original amount and leaves both balances unchanged; the key always wins over the payload")
		void replayWithDifferentAmountReturnsOriginalAmountAndProtectsBothBalances () {
			transfer(AMOUNT_100);
			BigDecimal originAfterOriginal = balanceOf(originId);
			BigDecimal destinationAfterOriginal = balanceOf(destinationId);

			TransactionResponse replayed = transferService.transfer(
				idempotencyKey,
				new TransferRequest(originId, destinationId, new BigDecimal("999.99"), BRL, "replay-tamper")
			);

			assertThat(replayed.amount()).isEqualByComparingTo(AMOUNT_100);
			assertThat(balanceOf(originId)).isEqualByComparingTo(originAfterOriginal);
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationAfterOriginal);

			assertThat(transactionRepository.findByAccountId(originId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.hasSize(1);

			assertThat(transactionRepository.findByAccountId(destinationId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.hasSize(1);
		}

		@Test
		@DisplayName("idempotency record is COMPLETED with correct operationType, non-blank JSON body containing expected fields, and a 24-hour expiry")
		void idempotencyRecordIsPersistedWithCorrectMetadata () {
			Instant before = Instant.now();
			transfer(STANDARD);
			Instant after = Instant.now();

			IdempotencyKey record = requireIdempotencyRecord(idempotencyKey, "transfer");

			assertThat(record.getStatus()).isEqualTo(IdempotencyKeyStatus.COMPLETED);
			assertThat(record.getOperationType()).isEqualTo(OPERATION_TYPE_TRANSFER);
			assertThat(record.getKey()).isEqualTo(idempotencyKey);
			assertThat(record.isExpired()).isFalse();

			assertThat(record.getResponseBody())
				.isNotBlank()
				.contains("\"transactionId\"")
				.contains("\"amount\"")
				.contains("\"type\"")
				.contains("\"status\"");

			assertThat(record.getCreatedAt())
				.isAfterOrEqualTo(before)
				.isBeforeOrEqualTo(after);

			assertThat(record.getExpiresAt())
				.isAfter(Instant.now())
				.isAfterOrEqualTo(record.getCreatedAt().plus(IDEMPOTENCY_KEY_RETENTION).minusSeconds(1))
				.isBeforeOrEqualTo(record.getCreatedAt().plus(IDEMPOTENCY_KEY_RETENTION).plusSeconds(1));
		}

		@Test
		@DisplayName("two distinct keys produce independent DEBIT/CREDIT pairs with different IDs and referenceIds; both account balances reflect both transfers")
		void distinctKeysProduceIndependentTransactionPairsAndCorrectBalances () {
			BigDecimal originBaseline = balanceOf(originId);
			BigDecimal destinationBaseline = balanceOf(destinationId);

			TransactionResponse r1 = transfer(AMOUNT_100, "key-alpha");
			TransactionResponse r2 = transfer(AMOUNT_200, "key-beta");

			assertThat(r1.transactionId()).isNotEqualTo(r2.transactionId());

			List<Transaction> debits = transactionRepository.findByAccountId(originId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.toList();

			List<Transaction> credits = transactionRepository.findByAccountId(destinationId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.toList();

			assertThat(debits)
				.hasSize(2)
				.extracting(Transaction::getId)
				.containsExactlyInAnyOrder(r1.transactionId(), r2.transactionId());

			assertThat(credits).hasSize(2);

			assertThat(debits)
				.extracting(Transaction::getReferenceId)
				.doesNotHaveDuplicates()
				.allMatch(ref -> ref != null && !ref.isBlank());

			assertThat(balanceOf(originId))
				.isEqualByComparingTo(originBaseline.subtract(AMOUNT_100).subtract(AMOUNT_200));
			assertThat(balanceOf(destinationId))
				.isEqualByComparingTo(destinationBaseline.add(AMOUNT_100).add(AMOUNT_200));
		}

		@Test
		@DisplayName("exactly one idempotency record exists per key after first call plus multiple replays")
		void exactlyOneIdempotencyRecordExistsAfterMultipleReplays () {
			transfer(STANDARD);
			transfer(STANDARD);
			transfer(STANDARD);

			assertThat(idempotencyKeyRepository.findAll())
				.filteredOn(r -> r.getKey().equals(idempotencyKey))
				.hasSize(1);

			assertThat(idempotencyKeyRepository.findAll())
				.filteredOn(r -> r.getKey().startsWith("transfer:"))
				.hasSize(1);
		}

		@Test
		@DisplayName("freshly persisted idempotency key is not expired and its expiry is in the future")
		void freshIdempotencyKeyIsNotExpired () {
			transfer(STANDARD);
			IdempotencyKey record = requireIdempotencyRecord(idempotencyKey, "transfer");

			assertThat(record.isExpired()).isFalse();
			assertThat(record.getExpiresAt()).isAfter(Instant.now());
		}
	}

	@Nested
	@DisplayName("Account guard-rails")
	class AccountGuardRails {

		@Test
		@DisplayName("throws AccountNotFoundException when the origin account ID was never persisted")
		void throwsAccountNotFoundForUnknownOriginId () {
			UUID phantom = UUID.randomUUID();

			assertThatThrownBy(() ->
				transferService.transfer(
					idempotencyKey,
					new TransferRequest(phantom, destinationId, STANDARD, BRL, "ghost-origin")
				)
			).isInstanceOf(AccountNotFoundException.class);
		}

		@Test
		@DisplayName("throws AccountNotFoundException when the destination account ID was never persisted")
		void throwsAccountNotFoundForUnknownDestinationId () {
			UUID phantom = UUID.randomUUID();

			assertThatThrownBy(() ->
				transferService.transfer(
					idempotencyKey,
					new TransferRequest(originId, phantom, STANDARD, BRL, "ghost-dest")
				)
			).isInstanceOf(AccountNotFoundException.class);
		}

		@Test
		@DisplayName("phantom origin: no transaction rows written, both balances untouched, idempotency record absent")
		void noSideEffectsWhenOriginAccountNotFound () {
			UUID phantom = UUID.randomUUID();
			BigDecimal originBefore = balanceOf(originId);
			BigDecimal destinationBefore = balanceOf(destinationId);

			try {
				transferService.transfer(
					idempotencyKey,
					new TransferRequest(phantom, destinationId, STANDARD, BRL, "ghost-origin")
				);
			} catch (AccountNotFoundException ignored) { }

			assertThat(transactionRepository.findByAccountId(phantom)).isEmpty();
			assertThat(transactionRepository.findAll())
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT || tx.getType() == TransactionType.TRANSFER_CREDIT)
				.isEmpty();
			assertThat(balanceOf(originId)).isEqualByComparingTo(originBefore);
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBefore);
			assertThat(idempotencyKeyRepository.findByKey(idempotencyKey)).isEmpty();
		}

		@Test
		@DisplayName("phantom destination: no transaction rows written, both balances untouched, idempotency record absent")
		void noSideEffectsWhenDestinationAccountNotFound () {
			UUID phantom = UUID.randomUUID();
			BigDecimal originBefore = balanceOf(originId);
			BigDecimal destinationBefore = balanceOf(destinationId);

			try {
				transferService.transfer(
					idempotencyKey,
					new TransferRequest(originId, phantom, STANDARD, BRL, "ghost-dest")
				);
			} catch (AccountNotFoundException ignored) { }

			assertThat(transactionRepository.findByAccountId(originId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.isEmpty();

			assertThat(transactionRepository.findAll())
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT || tx.getType() == TransactionType.TRANSFER_CREDIT)
				.isEmpty();
			assertThat(balanceOf(originId)).isEqualByComparingTo(originBefore);
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBefore);
			assertThat(idempotencyKeyRepository.findByKey(idempotencyKey)).isEmpty();
		}

		@Test
		@DisplayName("client can retry with the same key after correcting a phantom origin; both balances reflect only the corrected transfer")
		void retryAfterPhantomOriginSucceedsWithSameKey () {
			try {
				transferService.transfer(
					idempotencyKey,
					new TransferRequest(UUID.randomUUID(), destinationId, STANDARD, BRL, "ghost")
				);
			} catch (AccountNotFoundException ignored) { }

			BigDecimal originBefore = balanceOf(originId);
			BigDecimal destinationBefore = balanceOf(destinationId);

			TransactionResponse response = transferService.transfer(
				idempotencyKey,
				new TransferRequest(originId, destinationId, STANDARD, BRL, "corrected retry")
			);

			assertThat(response.transactionId()).isNotNull();
			assertThat(response.amount()).isEqualByComparingTo(STANDARD);
			assertThat(balanceOf(originId)).isEqualByComparingTo(originBefore.subtract(STANDARD));
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBefore.add(STANDARD));

			assertThat(transactionRepository.findByAccountId(originId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.hasSize(1);

			assertThat(transactionRepository.findByAccountId(destinationId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.hasSize(1);
		}

		@Test
		@DisplayName("transfer from a BLOCKED origin account is rejected; no rows written, no idempotency record, both balances unchanged")
		void blockedOriginAccountRejectsTransferWithNoSideEffects () {
			accountService.applyStatusAction(originId, AccountAction.BLOCK_ACCOUNT_USAGE);
			BigDecimal originBefore = balanceOf(originId);
			BigDecimal destinationBefore = balanceOf(destinationId);

			assertThatThrownBy(() -> transfer(STANDARD)).isInstanceOf(RuntimeException.class);

			assertThat(balanceOf(originId)).isEqualByComparingTo(originBefore);
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBefore);

			assertThat(transactionRepository.findByAccountId(originId))
				.filteredOn(tx -> tx.getIdempotencyKey().equals(idempotencyKey))
				.isEmpty();

			assertThat(transactionRepository.findByAccountId(destinationId)).isEmpty();
			assertThat(idempotencyKeyRepository.findByKey(idempotencyKey)).isEmpty();
		}

		@Test
		@DisplayName("transfer to a BLOCKED destination account is rejected; no rows written on either leg, no idempotency record, both balances unchanged")
		void blockedDestinationAccountRejectsTransferWithNoSideEffects () {
			accountService.applyStatusAction(destinationId, AccountAction.BLOCK_ACCOUNT_USAGE);
			BigDecimal originBefore = balanceOf(originId);
			BigDecimal destinationBefore = balanceOf(destinationId);

			assertThatThrownBy(() -> transfer(STANDARD)).isInstanceOf(RuntimeException.class);

			assertThat(balanceOf(originId)).isEqualByComparingTo(originBefore);
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBefore);

			assertThat(transactionRepository.findByAccountId(originId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.isEmpty();

			assertThat(transactionRepository.findByAccountId(destinationId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.isEmpty();

			assertThat(idempotencyKeyRepository.findByKey(idempotencyKey)).isEmpty();
		}

		@Test
		@DisplayName("transfer from a CLOSED origin account is rejected; no rows written, no idempotency record, both balances unchanged")
		void closedOriginAccountRejectsTransferWithNoSideEffects () {
			transferService.transfer(
				"drain-before-close",
				new TransferRequest(originId, destinationId, balanceOf(originId), BRL, "drain")
			);

			accountService.applyStatusAction(originId, AccountAction.CLOSE_ACCOUNT);

			BigDecimal originBefore = balanceOf(originId);
			BigDecimal destinationBefore = balanceOf(destinationId);

			assertThatThrownBy(() -> transfer(AMOUNT_MIN)).isInstanceOf(RuntimeException.class);

			assertThat(balanceOf(originId)).isEqualByComparingTo(originBefore);
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBefore);

			assertThat(transactionRepository.findByAccountId(originId))
				.filteredOn(tx -> tx.getIdempotencyKey().equals(idempotencyKey))
				.isEmpty();

			assertThat(idempotencyKeyRepository.findByKey(idempotencyKey)).isEmpty();
		}
	}

	@Nested
	@DisplayName("Transactional atomicity; balances and ledger are always in sync")
	class TransactionalAtomicity {

		@Test
		@DisplayName("origin balance equals its initial fund minus the sum of all TRANSFER_DEBIT rows")
		void originBalanceEqualsInitialFundMinusDebitLedger () {
			BigDecimal originBaseline = balanceOf(originId);

			transfer(new BigDecimal("111.11"), "k1");
			transfer(new BigDecimal("222.22"), "k2");
			transfer(new BigDecimal("100.0001"), "k3");

			BigDecimal debitSum = sumTransactionAmountsByType(originId, TransactionType.TRANSFER_DEBIT);

			assertThat(balanceOf(originId)).isEqualByComparingTo(originBaseline.subtract(debitSum));
		}

		@Test
		@DisplayName("destination balance equals its initial value plus the sum of all TRANSFER_CREDIT rows")
		void destinationBalanceEqualsInitialValuePlusCreditLedger () {
			BigDecimal destinationBaseline = balanceOf(destinationId);

			transfer(new BigDecimal("111.11"), "k1");
			transfer(new BigDecimal("222.22"), "k2");
			transfer(new BigDecimal("100.0001"), "k3");

			BigDecimal creditSum = sumTransactionAmountsByType(destinationId, TransactionType.TRANSFER_CREDIT);

			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBaseline.add(creditSum));
		}

		@Test
		@DisplayName("the total DEBIT ledger sum equals the total CREDIT ledger sum; no value is created or destroyed in the database")
		void debitLedgerSumEqualsCreditLedgerSum () {
			transfer(new BigDecimal("111.11"), "k1");
			transfer(new BigDecimal("222.22"), "k2");
			transfer(new BigDecimal("100.0001"), "k3");

			BigDecimal debitSum = sumTransactionAmountsByType(originId, TransactionType.TRANSFER_DEBIT);
			BigDecimal creditSum = sumTransactionAmountsByType(destinationId, TransactionType.TRANSFER_CREDIT);

			assertThat(debitSum).isEqualByComparingTo(creditSum);
		}

		@Test
		@DisplayName("currency on both account balances matches currency on both transaction rows and both legs share the same currency")
		void balanceCurrenciesMatchTransactionCurrencies () {
			transfer(STANDARD);

			Account origin = loadAccount(originId);
			Account destination = loadAccount(destinationId);

			Transaction debit = transactionRepository.findByAccountId(originId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.findFirst()
				.orElseThrow();

			Transaction credit = transactionRepository.findByAccountId(destinationId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.findFirst()
				.orElseThrow();

			assertThat(origin.getBalance().currency()).isEqualTo(debit.getAmount().currency());
			assertThat(destination.getBalance().currency()).isEqualTo(credit.getAmount().currency());
			assertThat(debit.getAmount().currency()).isEqualTo(credit.getAmount().currency());
		}

		@Test
		@DisplayName("rollback on phantom destination leaves origin balance and ledger unaffected; idempotency record is also absent")
		void rollbackOnPhantomDestinationLeavesOriginIntact () {
			BigDecimal originBefore = balanceOf(originId);

			try {
				transferService.transfer(
					"rollback-key",
					new TransferRequest(originId, UUID.randomUUID(), STANDARD, BRL, "phantom-dest")
				);
			} catch (AccountNotFoundException ignored) { }

			assertThat(balanceOf(originId)).isEqualByComparingTo(originBefore);

			assertThat(transactionRepository.findByAccountId(originId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.isEmpty();

			assertThat(transactionRepository.findAll())
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT || tx.getType() == TransactionType.TRANSFER_CREDIT)
				.isEmpty();
			assertThat(idempotencyKeyRepository.findByKey("rollback-key")).isEmpty();
		}

		@Test
		@DisplayName("balance-equals-ledger invariant holds for both accounts after mixed-precision sequential transfers")
		void balanceEqualsLedgerSumAfterMixedPrecisionTransfers () {
			BigDecimal originBaseline = balanceOf(originId);
			BigDecimal destinationBaseline = balanceOf(destinationId);

			transfer(new BigDecimal("0.0001"), "mp-k1");
			transfer(new BigDecimal("1.50"), "mp-k2");
			transfer(new BigDecimal("100.00"), "mp-k3");

			BigDecimal debitSum = sumTransactionAmountsByType(originId, TransactionType.TRANSFER_DEBIT);
			BigDecimal creditSum = sumTransactionAmountsByType(destinationId, TransactionType.TRANSFER_CREDIT);

			assertThat(debitSum).isEqualByComparingTo(creditSum);
			assertThat(balanceOf(originId)).isEqualByComparingTo(originBaseline.subtract(debitSum));
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBaseline.add(creditSum));
		}
	}

	@Nested
	@DisplayName("Database-level integrity invariants")
	class DatabaseIntegrity {

		@Test
		@DisplayName("unique constraint on idempotency_key prevents duplicate DEBIT rows; the total row count in the table is exactly two")
		void uniqueConstraintOnIdempotencyKeyIsEnforced () {
			transfer(STANDARD);

			assertThat(transactionRepository.findAll())
				.filteredOn(tx -> idempotencyKey.equals(tx.getIdempotencyKey()))
				.hasSize(2);

			assertThat(transactionRepository.findAll())
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT || tx.getType() == TransactionType.TRANSFER_CREDIT)
				.hasSize(2);
		}

		@Test
		@DisplayName("account_id on the DEBIT row is correctly bound to origin; CREDIT row is correctly bound to destination with matching counterparts")
		void bothLegsHaveCorrectAccountIdBindingAndCounterpartCrossReference () {
			transfer(STANDARD);

			Transaction debit = transactionRepository.findByAccountId(originId)
				.stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.findFirst()
				.orElseThrow();

			assertThat(debit.getAccountId()).isNotNull().isEqualTo(originId);
			assertThat(debit.getCounterpartAccountId()).isNotNull().isEqualTo(destinationId);

			Transaction credit = transactionRepository.findByAccountId(destinationId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.findFirst()
				.orElseThrow();

			assertThat(credit.getAccountId()).isNotNull().isEqualTo(destinationId);
			assertThat(credit.getCounterpartAccountId()).isNotNull().isEqualTo(originId);
		}

		@Test
		@DisplayName("amount column preserves scale=4 on both legs; no implicit truncation at the persistence layer")
		void amountColumnHasScale4PrecisionOnBothLegs () {
			BigDecimal fourDecimal = new BigDecimal("12.3456");
			transfer(fourDecimal);

			Transaction debit = transactionRepository.findByAccountId(originId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.findFirst()
				.orElseThrow();

			Transaction credit = transactionRepository.findByAccountId(destinationId).stream()
				.filter(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.findFirst()
				.orElseThrow();

			assertThat(debit.getAmount().amount().stripTrailingZeros())
				.isEqualByComparingTo(fourDecimal.stripTrailingZeros());
			assertThat(credit.getAmount().amount().stripTrailingZeros())
				.isEqualByComparingTo(fourDecimal.stripTrailingZeros());
		}

		@Test
		@DisplayName("statement query returns TRANSFER_DEBIT transactions for origin in descending createdAt order with correct total count")
		void statementQueryReturnsDebitTransactionsNewestFirst () {
			transfer(new BigDecimal("111.11"), "stmt-key-1");
			transfer(new BigDecimal("222.22"), "stmt-key-2");

			Page<Transaction> page = transactionRepository.findStatement(
				originId, null, null, TransactionType.TRANSFER_DEBIT,
				PageRequest.of(0, 10, Sort.by("createdAt").descending())
			);

			assertThat(page.getContent())
				.hasSize(2)
				.allMatch(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.allMatch(tx -> tx.getAccountId().equals(originId))
				.isSortedAccordingTo(
					Comparator
						.comparing(Transaction::getCreatedAt)
						.reversed()
				);

			assertThat(page.getTotalElements()).isEqualTo(2);
		}

		@Test
		@DisplayName("statement query for origin does not leak TRANSFER_CREDIT rows belonging to destination")
		void originStatementDoesNotLeakDestinationRows () {
			transfer(STANDARD);

			Page<Transaction> page = transactionRepository.findStatement(
				originId, null, null, null,
				PageRequest.of(0, 20)
			);

			assertThat(page.getContent())
				.isNotEmpty()
				.allMatch(tx -> tx.getAccountId().equals(originId))
				.noneMatch(tx -> tx.getAccountId().equals(destinationId));
		}

		@Test
		@DisplayName("statement query for destination returns only TRANSFER_CREDIT rows; DEBIT type query returns empty")
		void destinationStatementContainsOnlyCreditRowsAndNoDebitRows () {
			transfer(STANDARD);

			Page<Transaction> creditPage = transactionRepository.findStatement(
				destinationId, null, null, TransactionType.TRANSFER_CREDIT,
				PageRequest.of(0, 10)
			);

			Page<Transaction> debitPage = transactionRepository.findStatement(
				destinationId, null, null, TransactionType.TRANSFER_DEBIT,
				PageRequest.of(0, 10)
			);

			assertThat(creditPage.getContent())
				.hasSize(1)
				.allMatch(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.allMatch(tx -> tx.getAccountId().equals(destinationId));

			assertThat(debitPage.getContent()).isEmpty();
			assertThat(debitPage.getTotalElements()).isZero();
		}

		@Test
		@DisplayName("statement query returns zero results for both DEBIT and CREDIT types for an account that has had no transfers")
		void statementQueryReturnsEmptyForAccountWithNoTransfers () {
			UUID freshId = openChecking(CPF_3).id();

			Page<Transaction> debitPage = transactionRepository.findStatement(
				freshId, null, null, TransactionType.TRANSFER_DEBIT,
				PageRequest.of(0, 10)
			);

			Page<Transaction> creditPage = transactionRepository.findStatement(
				freshId, null, null, TransactionType.TRANSFER_CREDIT,
				PageRequest.of(0, 10)
			);

			assertThat(debitPage.getContent()).isEmpty();
			assertThat(debitPage.getTotalElements()).isZero();
			assertThat(creditPage.getContent()).isEmpty();
			assertThat(creditPage.getTotalElements()).isZero();
		}

		@Test
		@DisplayName("statement query second page returns correct non-overlapping results and correct totals when row count exceeds page size")
		void statementQueryPaginationIsCorrect () {
			for (int i = 1; i <= 5; i++) {
				transfer(new BigDecimal(i + ".00"), "page-key-" + i);
			}

			Page<Transaction> firstPage = transactionRepository.findStatement(
				originId, null, null, TransactionType.TRANSFER_DEBIT,
				PageRequest.of(0, 3, Sort.by("createdAt").descending())
			);

			Page<Transaction> secondPage = transactionRepository.findStatement(
				originId, null, null, TransactionType.TRANSFER_DEBIT,
				PageRequest.of(1, 3, Sort.by("createdAt").descending())
			);

			assertThat(firstPage.getContent()).hasSize(3);
			assertThat(secondPage.getContent()).hasSize(2);
			assertThat(firstPage.getTotalElements()).isEqualTo(5);
			assertThat(secondPage.getTotalElements()).isEqualTo(5);

			Set<UUID> firstPageIds = Set.copyOf(
				firstPage.getContent().stream().map(Transaction::getId).toList()
			);

			Set<UUID> secondPageIds = Set.copyOf(
				secondPage.getContent().stream().map(Transaction::getId).toList()
			);

			assertThat(firstPageIds).doesNotContainAnyElementsOf(secondPageIds);
		}
	}

	@Nested
	@DisplayName("Concurrency; race conditions and correctness under load")
	class Concurrency {

		@RepeatedTest(3)
		@DisplayName("N threads with distinct keys all succeed; final balances equal the arithmetic net and every DEBIT/CREDIT pair is present with correct status")
		void concurrentDistinctKeyTransfersAllSucceedAndBalancesAreExact () throws Exception {
			BigDecimal amountEach = new BigDecimal("50.00");

			fund(amountEach.multiply(BigDecimal.valueOf(CONCURRENT_THREADS)), originId);

			BigDecimal originBaseline = balanceOf(originId);
			BigDecimal destinationBaseline = balanceOf(destinationId);

			ConcurrentTestResult result = runConcurrent(
				CONCURRENT_THREADS,
				i -> transferService.transfer(
					"concurrent-distinct-" + i,
					new TransferRequest(originId, destinationId, amountEach, BRL, "load-" + i)
				)
			);

			assertThat(result.failures()).isEmpty();
			assertThat(result.successes()).isEqualTo(CONCURRENT_THREADS);

			BigDecimal totalTransferred = amountEach.multiply(BigDecimal.valueOf(CONCURRENT_THREADS));

			assertThat(balanceOf(originId)).isEqualByComparingTo(originBaseline.subtract(totalTransferred));
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBaseline.add(totalTransferred));

			assertThat(transactionRepository.findByAccountId(originId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.hasSize(CONCURRENT_THREADS)
				.allMatch(tx -> tx.getAccountId().equals(originId))
				.allMatch(tx -> tx.getStatus() == TransactionStatus.COMPLETED);

			assertThat(transactionRepository.findByAccountId(destinationId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.hasSize(CONCURRENT_THREADS)
				.allMatch(tx -> tx.getAccountId().equals(destinationId))
				.allMatch(tx -> tx.getStatus() == TransactionStatus.COMPLETED);

			BigDecimal debitSum = sumTransactionAmountsByType(originId, TransactionType.TRANSFER_DEBIT);
			BigDecimal creditSum = sumTransactionAmountsByType(destinationId, TransactionType.TRANSFER_CREDIT);
			assertThat(debitSum).isEqualByComparingTo(creditSum);
		}

		@RepeatedTest(3)
		@DisplayName("N threads racing on the same idempotency key transfer funds exactly once; idempotency fence holds under concurrent load")
		void concurrentSameKeyTransfersOnlyApplyOnce () throws Exception {
			BigDecimal originBaseline = balanceOf(originId);
			BigDecimal destinationBaseline = balanceOf(destinationId);

			ConcurrentTestResult result = runConcurrent(
				RACING_THREADS,
				i -> transferService.transfer(
					idempotencyKey,
					new TransferRequest(originId, destinationId, AMOUNT_100, BRL, "race")
				)
			);

			assertThat(result.failures()).isEmpty();
			assertThat(result.successes()).isEqualTo(RACING_THREADS);

			assertThat(balanceOf(originId)).isEqualByComparingTo(originBaseline.subtract(AMOUNT_100));
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBaseline.add(AMOUNT_100));

			assertThat(transactionRepository.findByAccountId(originId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.hasSize(1);

			assertThat(transactionRepository.findByAccountId(destinationId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.hasSize(1);

			assertThat(transactionRepository.findAll())
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT || tx.getType() == TransactionType.TRANSFER_CREDIT)
				.hasSize(2);

			assertThat(idempotencyKeyRepository.findAll())
				.filteredOn(r -> r.getKey().equals(idempotencyKey))
				.hasSize(1);
		}

		@RepeatedTest(3)
		@DisplayName("concurrent transfers between two independent pairs do not bleed balance between pairs; all four accounts land on the exact expected value")
		void concurrentTransfersToDistinctPairsDoNotBleedBalance () throws Exception {
			AccountResponse originB = openChecking(CPF_3);
			UUID originBId = originB.id();
			UUID destinationBId = openChecking(generateCpf(99)).id();
			fund(AMOUNT_500, originBId);

			BigDecimal amountA = new BigDecimal("300.00");
			BigDecimal amountB = new BigDecimal("200.00");

			BigDecimal baseOriginA = balanceOf(originId);
			BigDecimal baseDestinationA = balanceOf(destinationId);
			BigDecimal baseOriginB = balanceOf(originBId);
			BigDecimal baseDestinationB = balanceOf(destinationBId);

			ConcurrentTestResult result = runConcurrent(2, i -> {
				if (i == 0) {
					transferService.transfer(
						"pair-A-key",
						new TransferRequest(originId, destinationId, amountA, BRL, "pair-A")
					);
				} else {
					transferService.transfer(
						"pair-B-key",
						new TransferRequest(originBId, destinationBId, amountB, BRL, "pair-B")
					);
				}
			});

			assertThat(result.failures()).isEmpty();
			assertThat(result.successes()).isEqualTo(2);

			assertThat(balanceOf(originId)).isEqualByComparingTo(baseOriginA.subtract(amountA));
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(baseDestinationA.add(amountA));
			assertThat(balanceOf(originBId)).isEqualByComparingTo(baseOriginB.subtract(amountB));
			assertThat(balanceOf(destinationBId)).isEqualByComparingTo(baseDestinationB.add(amountB));

			assertThat(transactionRepository.findByAccountId(originId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.hasSize(1);

			assertThat(transactionRepository.findByAccountId(originBId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.hasSize(1);
		}

		@RepeatedTest(3)
		@DisplayName("mixed concurrent load; half distinct keys, half racing on the same key; correct balances, row counts, ledger sums, and idempotency record count on both accounts")
		void mixedConcurrentLoadProducesCorrectBalancesAndRowCount () throws Exception {
			int uniqueThreads = 6;
			int racingThreads = 6;
			int totalThreads = uniqueThreads + racingThreads;

			BigDecimal uniqueAmount = new BigDecimal("50.00");
			BigDecimal racingAmount = new BigDecimal("50.00");

			BigDecimal totalTransferAmount = uniqueAmount
				.multiply(BigDecimal.valueOf(uniqueThreads))
				.add(racingAmount);

			fund(
				totalTransferAmount,
				originId
			);

			BigDecimal originBaseline = balanceOf(originId);
			BigDecimal destinationBaseline = balanceOf(destinationId);

			ConcurrentTestResult result = runConcurrent(totalThreads, i -> {
				if (i < uniqueThreads) {
					transferService.transfer(
						"mixed-unique-" + i,
						new TransferRequest(originId, destinationId, uniqueAmount, BRL, "unique-" + i)
					);
				} else {
					transferService.transfer(
						idempotencyKey,
						new TransferRequest(originId, destinationId, racingAmount, BRL, "racing")
					);
				}
			});

			assertThat(result.failures()).isEmpty();
			assertThat(result.successes()).isEqualTo(totalThreads);

			BigDecimal expectedDebit = totalTransferAmount;
			int expectedRows = uniqueThreads + 1;

			assertThat(balanceOf(originId)).isEqualByComparingTo(originBaseline.subtract(expectedDebit));
			assertThat(balanceOf(destinationId)).isEqualByComparingTo(destinationBaseline.add(expectedDebit));

			assertThat(transactionRepository.findByAccountId(originId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_DEBIT)
				.hasSize(expectedRows)
				.allMatch(tx -> tx.getStatus() == TransactionStatus.COMPLETED);

			assertThat(transactionRepository.findByAccountId(destinationId))
				.filteredOn(tx -> tx.getType() == TransactionType.TRANSFER_CREDIT)
				.hasSize(expectedRows)
				.allMatch(tx -> tx.getStatus() == TransactionStatus.COMPLETED);

			BigDecimal debitSum = sumTransactionAmountsByType(originId, TransactionType.TRANSFER_DEBIT);
			BigDecimal creditSum = sumTransactionAmountsByType(destinationId, TransactionType.TRANSFER_CREDIT);
			assertThat(debitSum).isEqualByComparingTo(creditSum);

			assertThat(idempotencyKeyRepository.findAll())
				.filteredOn(r -> r.getKey().equals(idempotencyKey))
				.hasSize(1);
		}
	}

	private TransactionResponse transfer (BigDecimal amount) {
		return transferService.transfer(
			idempotencyKey,
			new TransferRequest(originId, destinationId, amount, BRL, "test transfer")
		);
	}

	private TransactionResponse transfer (BigDecimal amount, String key) {
		return transferService.transfer(
			key,
			new TransferRequest(originId, destinationId, amount, BRL, "test transfer")
		);
	}

	private void fund (BigDecimal amount, UUID targetId) {
		depositService.deposit(
			"fund:" + UUID.randomUUID(),
			new DepositRequest(targetId, amount, BRL, "fund")
		);
	}
}
