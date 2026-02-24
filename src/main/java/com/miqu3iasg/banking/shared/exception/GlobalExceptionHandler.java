package com.miqu3iasg.banking.shared.exception;

import com.miqu3iasg.banking.shared.exception.metrics.ErrorMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {

	private final ErrorMetrics errorMetrics;

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusinessException (BusinessException ex) {
		log.warn("Business exception [{}]: {}", ex.getErrorCode(), ex.getMessage());

		errorMetrics.recordError(ex, ex.getHttpStatus());

		return ResponseEntity
			.status(ex.getHttpStatus())
			.body(ErrorResponse.from(ex));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationException (MethodArgumentNotValidException ex) {
		Map<String, Object> fieldErrors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.collect(Collectors.toMap(
				FieldError::getField,
				error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "invalid value",
				(existing, duplicate) -> existing
			));

		log.warn("Validation failed for {} field(s): {}", fieldErrors.size(), fieldErrors.keySet());

		errorMetrics.recordError(ex, HttpStatus.UNPROCESSABLE_ENTITY.value());

		return ResponseEntity
			.status(HttpStatus.UNPROCESSABLE_ENTITY)
			.body(ErrorResponse.of(
				"VALIDATION_ERROR",
				"One or more fields failed validation",
				HttpStatus.UNPROCESSABLE_ENTITY.value(),
				fieldErrors
			));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadableMessage (HttpMessageNotReadableException ex) {
		log.warn("Unreadable HTTP message: {}", ex.getMessage());

		errorMetrics.recordError(ex, HttpStatus.BAD_REQUEST.value());

		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(ErrorResponse.of(
				"MALFORMED_REQUEST",
				"Request body is missing or cannot be parsed",
				HttpStatus.BAD_REQUEST.value()
			));
	}

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound (NoHandlerFoundException ex) {
		return ResponseEntity
			.status(HttpStatus.NOT_FOUND)
			.body(ErrorResponse.of(
				"ROUTE_NOT_FOUND",
				"The requested route does not exist: " + ex.getRequestURL(),
				HttpStatus.NOT_FOUND.value()
			));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupported (HttpRequestMethodNotSupportedException ex) {
		return ResponseEntity
			.status(HttpStatus.METHOD_NOT_ALLOWED)
			.body(ErrorResponse.of(
				"METHOD_NOT_ALLOWED",
				"HTTP method '%s' is not supported for this endpoint".formatted(ex.getMethod()),
				HttpStatus.METHOD_NOT_ALLOWED.value()
			));
	}

	@ExceptionHandler({
		AccountNumberGenerationException.class,
		IdempotencyCacheCorruptException.class,
		PurgeInterruptedException.class
	})
	public ResponseEntity<ErrorResponse> handleInfrastructureException (RuntimeException ex) {
		log.error("Infrastructure failure [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);

		errorMetrics.recordError(ex, HttpStatus.INTERNAL_SERVER_ERROR.value());

		return ResponseEntity
			.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ErrorResponse.of(
				"INTERNAL_ERROR",
				"An internal system error occurred. Please contact support.",
				HttpStatus.INTERNAL_SERVER_ERROR.value()
			));
	}


	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpectedException (Exception ex) {
		log.error("Unhandled exception [{}]: {}", ex.getClass().getName(), ex.getMessage(), ex);

		errorMetrics.recordError(ex, HttpStatus.INTERNAL_SERVER_ERROR.value());

		return ResponseEntity
			.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ErrorResponse.of(
				"INTERNAL_ERROR",
				"An unexpected error occurred.",
				HttpStatus.INTERNAL_SERVER_ERROR.value()
			));
	}
}
