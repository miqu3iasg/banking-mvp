package com.miqu3iasg.banking.pix.domain;

public enum PixKeyType {
	CPF,
	CNPJ,
	EMAIL,
	PHONE,
	RANDOM;

	public static PixKeyType fromString (String type) {
		try {
			return PixKeyType.valueOf(type.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid Pix key type: " + type);
		}
	}
}
