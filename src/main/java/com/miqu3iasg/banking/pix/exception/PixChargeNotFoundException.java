package com.miqu3iasg.banking.pix.exception;

import com.miqu3iasg.banking.shared.exception.BusinessException;

import java.util.Map;

public class PixChargeNotFoundException extends BusinessException {
	public PixChargeNotFoundException (String txid) {
		super(
			String.format("Pix charge with txid %s not found", txid),
			PixFaultCode.PIX_CHARGE_NOT_FOUND,
			Map.of("txid", txid),
			null
		);
	}
}
