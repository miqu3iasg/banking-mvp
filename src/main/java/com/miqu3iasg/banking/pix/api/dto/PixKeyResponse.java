package com.miqu3iasg.banking.pix.api.dto;

import com.miqu3iasg.banking.pix.domain.PixKey;

public record PixKeyResponse(
	String id,
	String accountId,
	String keyType,
	String keyValue,
	String status,
	String createdAt,
	String updatedAt
) {
	public static PixKeyResponse from(PixKey source) {
		return new PixKeyResponse(
			source.getId().toString(),
			source.getAccountId().toString(),
			source.getType().name(),
			source.getValue(),
			source.getStatus().name(),
			source.getCreatedAt().toString(),
			source.getUpdatedAt().toString()
		);
	}
}
