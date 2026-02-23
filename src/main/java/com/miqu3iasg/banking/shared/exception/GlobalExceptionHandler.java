package com.miqu3iasg.banking.shared.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusinessException (BusinessException ex) {
		return ResponseEntity
			.status(ex.getHttpStatus())
			.body(ErrorResponse.from(ex));
	}

	public record ErrorResponse(
		String code,
		String message,
		int status,
		Instant timestamp,
		Map<String, Object> context
	) {
		static ErrorResponse from (BusinessException ex) {
			return new ErrorResponse(
				ex.getFaultCode().getCode(),
				ex.getMessage(),
				ex.getHttpStatus(),
				ex.getTimestamp(),
				ex.getContext()
			);
		}
	}
}
