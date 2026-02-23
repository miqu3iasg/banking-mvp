package com.miqu3iasg.banking.shared.exception;

public class PurgeInterruptedException extends RuntimeException {
	public PurgeInterruptedException (InterruptedException cause) {
		super("Idempotency purge interrupted during batch delay", cause);
	}
}
