package com.miqu3iasg.banking.pix.gateway;

import com.miqu3iasg.banking.pix.domain.PixCharge;

public record PixChargeResponse(
	String txid,
	String status,
	String copyPaste
) {
	public static PixChargeResponse from (PixCharge charge) {
		return new PixChargeResponse(
			charge.getTxid(),
			charge.getStatus().name(),
			charge.getCopyPaste()
		);
	}
}
