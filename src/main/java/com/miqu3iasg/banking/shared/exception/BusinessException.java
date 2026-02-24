package com.miqu3iasg.banking.shared.exception;

import com.miqu3iasg.banking.shared.exception.code.FaultCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

@Getter
public class BusinessException extends RuntimeException {

	private final FaultCode faultCode;
	private final Instant timestamp;
	private final Map<String, Object> context;

	protected BusinessException (String message, FaultCode faultCode) {
		this(message, faultCode, Collections.emptyMap(), null);
	}

	protected BusinessException (String message, FaultCode faultCode, Throwable cause) {
		this(message, faultCode, Collections.emptyMap(), cause);
	}

	protected BusinessException (
		String message,
		FaultCode faultCode,
		Map<String, Object> context,
		Throwable cause
	) {
		super(message, cause, true, false);
		this.faultCode = validateFaultCode(faultCode);
		this.timestamp = Instant.now();
		this.context = Collections.unmodifiableMap(context);
	}

	public static BusinessException of (FaultCode faultCode) {
		return new BusinessException(faultCode.getDefaultMessage(), faultCode);
	}

	public static BusinessException of (FaultCode faultCode, Map<String, Object> context) {
		return new BusinessException(faultCode.getDefaultMessage(), faultCode, context, null);
	}

	public static BusinessException of (
		String message,
		FaultCode faultCode,
		Map<String, Object> context
	) {
		return new BusinessException(message, faultCode, context, null);
	}

	public static BusinessException wrapping (FaultCode faultCode, Throwable cause) {
		return new BusinessException(faultCode.getDefaultMessage(), faultCode, Collections.emptyMap(), cause);
	}

	public int getHttpStatus () {
		return faultCode.getHttpStatus();
	}

	private static FaultCode validateFaultCode (FaultCode faultCode) {
		if (faultCode == null) {
			throw new IllegalArgumentException("faultCode must not be null");
		}
		return faultCode;
	}

	public String getErrorCode () {
		return faultCode.getCode();
	}

	@Override
	public String toString () {
		return String.format(
			"%s[code=%s, httpStatus=%d, timestamp=%s, message=%s, context=%s]",
			getClass().getSimpleName(),
			faultCode.getCode(),
			faultCode.getHttpStatus(),
			timestamp,
			getMessage(),
			context
		);
	}
}
