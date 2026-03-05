package com.miqu3iasg.banking.pix.gateway;

import java.util.Optional;

public interface PixGateway {
	PixChargeCreationResponse createCharge(PixChargeRequest request);

	Optional<PixChargeResponse> getCharge(String txid);

	void cancelCharge(String txid);
}
