package com.miqu3iasg.banking.shared.exception;

public class IdempotencyTimeoutException extends RuntimeException {
	private final String idempotencyKey;

	public IdempotencyTimeoutException (String idempotencyKey) {
		super("Timed out waiting for idempotency winner: key=[%s]".formatted(idempotencyKey));
		this.idempotencyKey = idempotencyKey;
	}

	public String getIdempotencyKey () {
		return idempotencyKey;
	}
}
