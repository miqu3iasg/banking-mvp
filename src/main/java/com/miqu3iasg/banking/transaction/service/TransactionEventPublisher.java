package com.miqu3iasg.banking.transaction.service;

import com.miqu3iasg.banking.account.domain.Account;
import com.miqu3iasg.banking.transaction.domain.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class TransactionEventPublisher {

	private final ApplicationEventPublisher eventPublisher;

	public void schedulePostCommitEvent (Transaction transaction) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit () {
				eventPublisher.publishEvent(
					TransactionCompletedEvent.ofSingleAccount(transaction)
				);
			}
		});
	}

	public void schedulePostCommitEvents (
		Transaction debitTransaction,
		Transaction creditTransaction,
		AccountPair accounts
	) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit () {
				eventPublisher.publishEvent(
					TransactionCompletedEvent.ofTransferLeg(
						debitTransaction,
						accounts.destination().getId()
					)
				);

				eventPublisher.publishEvent(
					TransactionCompletedEvent.ofTransferLeg(
						creditTransaction,
						accounts.origin().getId()
					)
				);
			}
		});
	}

	public record AccountPair(Account origin, Account destination) { }
}
