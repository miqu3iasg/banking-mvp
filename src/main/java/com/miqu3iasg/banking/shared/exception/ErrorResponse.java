package com.miqu3iasg.banking.shared.exception;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
	String code,
	String message,
	int status,
	Instant timestamp,
	Map<String, Object> context
) {
	public static ErrorResponse from (BusinessException ex) {
		return new ErrorResponse(
			ex.getFaultCode().getCode(),
			ex.getMessage(),
			ex.getHttpStatus(),
			ex.getTimestamp(),
			ex.getContext()
		);
	}

	public static ErrorResponse of (String code, String message, int status) {
		return new ErrorResponse(code, message, status, Instant.now(), Map.of());
	}

	public static ErrorResponse of (String code, String message, int status, Map<String, Object> context) {
		return new ErrorResponse(code, message, status, Instant.now(), context);
	}
}
