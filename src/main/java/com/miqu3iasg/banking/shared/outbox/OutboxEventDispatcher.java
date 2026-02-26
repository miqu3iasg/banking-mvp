package com.miqu3iasg.banking.shared.outbox;

public interface OutboxEventDispatcher {
	String eventType ();

	void dispatch (OutboxEvent event) throws Exception;
}
