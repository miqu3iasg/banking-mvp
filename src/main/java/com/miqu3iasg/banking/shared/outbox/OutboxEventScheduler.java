package com.miqu3iasg.banking.shared.outbox;

import com.miqu3iasg.banking.shared.scheduler.Scheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for processing outbox events.
 * <p>
 * Runs every 5 seconds to process pending outbox events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventScheduler implements Scheduler {

    private final OutboxProcessor outboxProcessor;

    @Override
    public String getName() {
        return "OutboxEventScheduler";
    }

    @Override
    public String getCronExpression() {
        return "*/5 * * * * *";
    }

    /**
     * Scheduled method to process outbox events.
     * Delegates to OutboxProcessor for the actual business logic.
     */
    @Scheduled(fixedDelayString = "${outbox.processor.interval-ms:5000}")
    public void processOutboxEvents() {
        execute();
    }

    @Override
    public void execute() {
        outboxProcessor.poolAndProcess();
    }
}
