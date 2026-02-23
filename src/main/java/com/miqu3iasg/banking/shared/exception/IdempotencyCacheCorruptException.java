package com.miqu3iasg.banking.shared.exception;

public class IdempotencyCacheCorruptException extends IllegalStateException {
	public IdempotencyCacheCorruptException (Class<?> targetType, String payload, Throwable cause) {
		super("Corrupt idempotency cache entry: cannot deserialize to [%s]. Payload (truncated): [%.200s]"
			.formatted(targetType.getName(), payload), cause);
	}
}
