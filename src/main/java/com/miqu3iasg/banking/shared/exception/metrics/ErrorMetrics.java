package com.miqu3iasg.banking.shared.exception.metrics;

import com.miqu3iasg.banking.shared.exception.AccountNumberGenerationException;
import com.miqu3iasg.banking.shared.exception.BusinessException;
import com.miqu3iasg.banking.shared.exception.IdempotencyCacheCorruptException;
import com.miqu3iasg.banking.shared.exception.PurgeInterruptedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ErrorMetrics {

	private static final String ERRORS = "banking.errors.total";

	private static final String TAG_FAULT_CODE = "fault_code";
	private static final String TAG_HTTP_STATUS = "http_status";
	private static final String TAG_EXCEPTION_TYPE = "exception_type";
	private static final String TAG_ERROR_CLASS = "error_class";

	private static final String ERROR_CLASS_BUSINESS = "business";
	private static final String ERROR_CLASS_INFRASTRUCTURE = "infrastructure";
	private static final String ERROR_CLASS_UNEXPECTED = "unexpected";
	private static final String UNKNOWN = "unknown";

	private final MeterRegistry registry;

	public ErrorMetrics (MeterRegistry registry) {
		this.registry = registry;
	}

	public void recordError (Exception ex, int httpStatus) {
		Counter.builder(ERRORS)
			.description("Total number of errors handled by the global exception handler")
			.tag(TAG_FAULT_CODE, extractFaultCode(ex))
			.tag(TAG_HTTP_STATUS, String.valueOf(httpStatus))
			.tag(TAG_EXCEPTION_TYPE, ex.getClass().getSimpleName())
			.tag(TAG_ERROR_CLASS, extractErrorClass(ex))
			.register(registry)
			.increment();
	}

	private String extractFaultCode (Exception ex) {
		return ex instanceof BusinessException be ? be.getErrorCode() : UNKNOWN;
	}

	private String extractErrorClass (Exception ex) {
		if (ex instanceof BusinessException) return ERROR_CLASS_BUSINESS;
		if (isInfrastructureException(ex)) return ERROR_CLASS_INFRASTRUCTURE;
		return ERROR_CLASS_UNEXPECTED;
	}

	private boolean isInfrastructureException (Exception ex) {
		return ex instanceof AccountNumberGenerationException
			|| ex instanceof IdempotencyCacheCorruptException
			|| ex instanceof PurgeInterruptedException;
	}
}
