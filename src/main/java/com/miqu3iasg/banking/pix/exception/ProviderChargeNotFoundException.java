package com.miqu3iasg.banking.pix.exception;

public class ProviderChargeNotFoundException extends RuntimeException {
	public ProviderChargeNotFoundException (String txid) {
		super("Charge not found at Efí Bank: " + txid);
	}
}
