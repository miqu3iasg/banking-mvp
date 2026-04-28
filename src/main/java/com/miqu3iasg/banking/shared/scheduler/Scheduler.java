package com.miqu3iasg.banking.shared.scheduler;

/**
 * Common interface for all schedulers to ensure consistent implementation.
 */
public interface Scheduler {
    /**
     * Execute the scheduler's main logic.
     */
    void execute();

    /**
     * Get the name of this scheduler.
     * @return the scheduler name
     */
    String getName();

    /**
     * Get the cron expression for this scheduler.
     * @return the cron expression
     */
    String getCronExpression();
}