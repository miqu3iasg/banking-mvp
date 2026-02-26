package com.miqu3iasg.banking.shared.audit;

import com.miqu3iasg.banking.transaction.domain.TransactionStatus;
import com.miqu3iasg.banking.transaction.domain.TransactionType;

public enum AuditAction {
	CREATE,
	UPDATE,
	DELETE,
	STATUS_CHANGE,
	DEPOSIT,
	WITHDRAWAL,
	TRANSFER,
	PIX,
	BOLETO_PAYMENT,
	REVERSAL;

	public static AuditAction from (TransactionType type, TransactionStatus status) {
		if (status == TransactionStatus.REVERSED) {
			return REVERSAL;
		}

		return switch (type) {
			case CREDIT -> DEPOSIT;
			case DEBIT -> WITHDRAWAL;
			case TRANSFER_DEBIT, TRANSFER_CREDIT -> TRANSFER;
			case PIX_DEBIT, PIX_CREDIT -> PIX;
			case BOLETO_PAYMENT -> BOLETO_PAYMENT;
		};
	}
}
