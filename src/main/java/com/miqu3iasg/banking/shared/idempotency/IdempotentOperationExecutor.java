package com.miqu3iasg.banking.shared.idempotency;

import com.miqu3iasg.banking.shared.exception.IdempotencyTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentOperationExecutor {

	private final IdempotencyService idempotencyService;

	public <T> T execute (
		String idempotencyKey,
		String operation,
		Class<T> responseType,
		Supplier<T> action
	) {
		var cached = idempotencyService.findCachedResponse(idempotencyKey, responseType);
		if (cached.isPresent()) {
			log.debug("idempotency HIT: operation=[{}] key=[{}]", operation, idempotencyKey);
			return cached.get();
		}

		boolean winner = idempotencyService.claimKey(idempotencyKey, operation);
		if (!winner) {
			log.debug("idempotency LOSER: operation=[{}] key=[{}]; awaiting winner", operation, idempotencyKey);
			return idempotencyService
				.awaitCompletedResponse(idempotencyKey, responseType)
				.orElseThrow(() -> new IdempotencyTimeoutException(idempotencyKey));
		}

		try {
			T response = action.get();
			idempotencyService.completeKey(idempotencyKey, operation, response);
			return response;
		} catch (Exception e) {
			idempotencyService.deletePendingKey(idempotencyKey);
			throw e;
		}
	}
}
