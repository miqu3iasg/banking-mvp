package com.miqu3iasg.banking_mvp.efi.pix.gateway;

/**
 * Constants derived from the BACEN Pix specification.
 */
public final class BacenSpec {
	public static final int TXID_LENGTH = 26;
	public static final String EMV_QR_CODE_PREFIX = "00020101";
	public static final String EFI_LOCATION_DOMAIN_PROD    = "pix.sejaefi.com.br";
	public static final String EFI_LOCATION_DOMAIN_SANDBOX = "qrcodespix-h.sejaefi.com.br";

	private BacenSpec () { }
}
