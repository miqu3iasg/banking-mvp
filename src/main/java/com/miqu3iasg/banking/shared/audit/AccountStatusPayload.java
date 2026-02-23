package com.miqu3iasg.banking.shared.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.SneakyThrows;

@Builder
public record AccountStatusPayload(
	String accountNumber,
	String status,
	String reason
) {
	private static final ObjectMapper mapper = new ObjectMapper();

	@SneakyThrows
	public String toJson() {
		return mapper.writeValueAsString(this);
	}
}
