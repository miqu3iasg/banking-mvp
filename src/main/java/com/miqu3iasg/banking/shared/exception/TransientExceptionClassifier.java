package com.miqu3iasg.banking.shared.exception;

import com.miqu3iasg.banking.pix.exception.PixAuthenticationException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.TransientDataAccessException;

public class TransientExceptionClassifier {
	private TransientExceptionClassifier () { }

	public static boolean isRetryable(Throwable t) {
		return t instanceof OptimisticLockException
			|| t instanceof TransientDataAccessException
			|| t instanceof PixAuthenticationException;
	}

	public static boolean isNonRetryable(Throwable t) {
		return !isRetryable(t);
	}
}
