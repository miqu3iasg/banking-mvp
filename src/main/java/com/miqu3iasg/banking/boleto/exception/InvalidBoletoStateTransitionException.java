package com.miqu3iasg.banking.boleto.exception;

import com.miqu3iasg.banking.boleto.domain.BoletoStatus;
import com.miqu3iasg.banking.shared.exception.BusinessException;

import java.util.Map;

public class InvalidBoletoStateTransitionException extends BusinessException {
	public InvalidBoletoStateTransitionException (String chargeId, BoletoStatus current, BoletoStatus target) {
		super(
			"Invalid boleto state transition from %s to %s for chargeId=%s"
				.formatted(current, target, chargeId),
			BoletoFaultCode.INVALID_BOLETO_STATE_TRANSITION,
			Map.of(
				"chargeId", chargeId,
				"currentStatus", current,
				"targetStatus", target
			),
			null
		);
	}
}
