package com.miqu3iasg.banking.transaction.service;

import com.miqu3iasg.banking.account.repository.AccountRepository;
import com.miqu3iasg.banking.shared.exception.AccountNotFoundException;
import com.miqu3iasg.banking.transaction.api.dto.TransactionResponse;
import com.miqu3iasg.banking.transaction.domain.Transaction;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import com.miqu3iasg.banking.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionHistoryService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public Page<TransactionResponse> findTransactions(
            UUID accountId, Instant from, Instant to, TransactionType type, Pageable pageable) {

        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }

        log.debug("Fetching transaction history for accountId={} from={} to={} type={}",
                accountId, from, to, type);

        Page<Transaction> page = transactionRepository.findStatement(accountId, from, to, type, pageable);

        return page.map(TransactionResponse::from);
    }
}
