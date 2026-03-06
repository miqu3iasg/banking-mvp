package com.miqu3iasg.banking.boleto.gateway;

public record BoletoIssuanceResponse(
	long providerChargeId,
	String barcode,
	String billetLink,
	String pdfUrl
) {
}
