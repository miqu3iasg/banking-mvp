package com.miqu3iasg.banking.pix.api.dto;

public record RegisterPixKeyRequest(
	String keyType,
	String keyValue
) {
}
