package com.miqu3iasg.banking.boleto.exception;

import java.util.Map;
import java.util.UUID;

import com.miqu3iasg.banking.shared.exception.BusinessException;

public class BoletoNotFoundException extends BusinessException {

	public BoletoNotFoundException (UUID boletoId) {
		super(
			"Boleto not found: " + boletoId,
			BoletoFaultCode.BOLETO_NOT_FOUND,
			Map.of("boletoId", boletoId),
			null
		);
	}

	public BoletoNotFoundException (long providerChargeId) {
		super(
			"Boleto not found for providerChargeId: " + providerChargeId,
			BoletoFaultCode.BOLETO_NOT_FOUND,
			Map.of("providerChargeId", providerChargeId),
			null
		);
	}
}
