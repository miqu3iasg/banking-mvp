package com.miqu3iasg.banking.shared.idempotency;

import com.miqu3iasg.banking.shared.scheduler.Scheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for purging expired idempotency keys.
 * <p>
 * Runs daily at 00:00 (midnight) to clean up expired idempotency records.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyPurgeScheduler implements Scheduler {

    private final IdempotencyService idempotencyService;

    @Override
    public String getName() {
        return "IdempotencyPurgeScheduler";
    }

    @Override
    public String getCronExpression() {
        return "0 0 0 * * *";
    }

    /**
     * Scheduled method to purge expired idempotency keys.
     * Delegates to IdempotencyService for the actual business logic.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void purgeExpiredKeys() {
        execute();
    }

    @Override
    public void execute() {
        idempotencyService.purgeExpiredKeysInternal();
    }
}
