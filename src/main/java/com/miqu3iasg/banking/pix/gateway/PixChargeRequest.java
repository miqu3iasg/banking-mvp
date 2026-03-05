package com.miqu3iasg.banking.pix.gateway;

import com.miqu3iasg.banking.pix.domain.PixCharge;

import java.math.BigDecimal;

public record PixChargeRequest(
	String txid,
	BigDecimal amount,
	String payerName,
	String payerCpfCnpj,
	String pixKey,
	int expiresInSeconds
) {

	public static PixChargeRequest from(PixCharge charge, String pixKey, int expiresInSeconds) {
		return new PixChargeRequest(
			charge.getTxid(),
			charge.getAmount(),
			charge.getPayerName(),
			charge.getPayerCpfCnpj(),
			pixKey,
			expiresInSeconds
		);
	}
}
