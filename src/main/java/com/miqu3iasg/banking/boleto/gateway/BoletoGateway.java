package com.miqu3iasg.banking.boleto.gateway;

public interface BoletoGateway {
	BoletoIssuanceResponse issueBoleto (BoletoIssuanceRequest request);

	String getChargeStatus (long chargeId);
}
