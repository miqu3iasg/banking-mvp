package com.miqu3iasg.banking.pix.domain;

public enum PixChargeStatus {
	PENDING,
	PAID,
	CANCELLED,
	EXPIRED;

	public boolean canTransitionTo (PixChargeStatus next) {
		return switch (this) {
			case PENDING -> next == PAID || next == CANCELLED || next == EXPIRED;
			case PAID, CANCELLED, EXPIRED -> false;
		};
	}

	public boolean isPaid () {
		return this == PAID;
	}
}
