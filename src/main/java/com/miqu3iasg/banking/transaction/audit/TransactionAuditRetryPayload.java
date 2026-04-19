package com.miqu3iasg.banking.transaction.audit;

import java.time.Instant;

public record TransactionAuditRetryPayload(
    int schemaVersion,
    String transactionId,
    String accountId,
    String counterpartAccountId,
    String type,
    String status,
    String amount,
    String currency,
    String referenceId,
    String occurredAt,
    String description
) { }
