package com.miqu3iasg.banking.boleto.exception;

import java.util.Map;

import com.miqu3iasg.banking.shared.exception.BusinessException;

public class BoletoNotFoundException extends BusinessException {

	public BoletoNotFoundException (long providerChargeId) {
		super(
			"Boleto not found for providerChargeId: " + providerChargeId,
			BoletoFaultCode.BOLETO_NOT_FOUND,
			Map.of("providerChargeId", providerChargeId),
			null
		);
	}
}
