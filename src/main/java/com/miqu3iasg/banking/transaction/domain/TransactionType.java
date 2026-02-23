package com.miqu3iasg.banking.transaction.domain;

public enum TransactionType {
	DEBIT,
	CREDIT,

	TRANSFER_DEBIT,
	TRANSFER_CREDIT,

	PIX_DEBIT,
	PIX_CREDIT,
	BOLETO_PAYMENT;
}
