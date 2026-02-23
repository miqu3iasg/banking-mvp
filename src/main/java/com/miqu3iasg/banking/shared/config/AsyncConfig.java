package com.miqu3iasg.banking.shared.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async executor for domain event listeners.
 * <p>
 * Replaces Spring's default {@code SimpleAsyncTaskExecutor}, which spawns unbounded threads
 * and will exhaust OS resources under sustained load. All {@code @Async} listeners that declare
 * {@code "accountEventExecutor"} share this pool.
 * <p>
 * Listeners are I/O-bound (DB writes, HTTP calls), so thread count exceeds CPU core count
 * intentionally. Core=4, Max=10 is a conservative baseline — validate with load tests against
 * real infrastructure before adjusting.
 * <p>
 * {@code @Async} crosses a thread boundary, which silently drops MDC context. The
 * {@link org.springframework.core.task.TaskDecorator} captures the caller's MDC snapshot
 * before dispatch and restores it on the executor thread:
 *
 * <pre>{@code
 * Map<String, String> callerMdc = MDC.getCopyOfContextMap();
 * return () -> {
 *     try {
 *         if (callerMdc != null) MDC.setContextMap(callerMdc);
 *         else MDC.clear();
 *         runnable.run();
 *     } finally {
 *         MDC.clear();
 *     }
 * };
 * }</pre>
 * <p>
 * Queue capacity is bounded at 100. When all threads are busy and the queue is full,
 * {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy} executes the rejected task
 * on the publishing thread — typically a web request thread. This trades request latency for
 * guaranteed delivery. If that trade-off is unacceptable, replace with a dead-letter strategy
 * or circuit breaker.
 * <p>
 * The executor waits up to 30 seconds for in-flight tasks on shutdown, preventing silent
 * data loss during restarts or deployments.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	/**
	 * Bounded thread pool for account domain event listeners.
	 * <p>
	 * Declare explicitly on listeners via:
	 *
	 * <pre>{@code @Async("accountEventExecutor")}</pre>
	 *
	 * @return configured {@link Executor} ready for async event dispatch
	 */
	@Bean(name = "accountEventExecutor")
	public Executor accountEventExecutor () {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(10);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("account-event-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

		executor.setTaskDecorator(runnable -> {
			Map<String, String> callerMdc = MDC.getCopyOfContextMap();

			return () -> {
				try {
					if (callerMdc != null) {
						MDC.setContextMap(callerMdc);
					} else {
						MDC.clear();
					}

					runnable.run();
				} finally {
					MDC.clear();
				}
			};
		});

		executor.initialize();

		return executor;
	}
}
