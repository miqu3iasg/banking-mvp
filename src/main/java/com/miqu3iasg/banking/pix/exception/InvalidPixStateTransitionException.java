package com.miqu3iasg.banking.pix.exception;

import com.miqu3iasg.banking.pix.domain.PixChargeStatus;
import com.miqu3iasg.banking.shared.exception.BusinessException;

import java.util.Map;

public class InvalidPixStateTransitionException extends BusinessException {
	public InvalidPixStateTransitionException (String txid, PixChargeStatus current, PixChargeStatus target) {
		super(
			PixFaultCode.INVALID_PIX_STATE_TRANSITION.getDefaultMessage(),
			PixFaultCode.INVALID_PIX_STATE_TRANSITION,
			Map.of(
				"txid", txid,
				"currentStatus", current.name(),
				"targetStatus", target.name()
			),
			null
		);
	}
}
