package com.miqu3iasg.banking.boleto.gateway;

public interface BoletoGateway {
	BoletoIssuanceResponse issue (BoletoIssuanceRequest request);

	String getChargeStatus (long chargeId);
}
