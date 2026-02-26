package com.miqu3iasg.banking.transaction.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.SneakyThrows;

@Builder
public record TransactionAuditPayload(
	String transactionId,
	String accountId,
	String counterpartAccountId,
	String type,
	String status,
	String amount,
	String currency,
	String description,
	String referenceId
) {
	private static final ObjectMapper mapper = new ObjectMapper();

	@SneakyThrows
	public String toJson () {
		return mapper.writeValueAsString(this);
	}
}
