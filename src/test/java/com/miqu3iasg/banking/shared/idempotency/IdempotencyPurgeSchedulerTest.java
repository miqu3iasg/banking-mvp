package com.miqu3iasg.banking.shared.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdempotencyPurgeSchedulerTest {

    @Mock
    private IdempotencyService idempotencyService;

    private IdempotencyPurgeScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new IdempotencyPurgeScheduler(idempotencyService);
    }

    @Test
    void purgeExpiredKeys_shouldDelegateToService() {
        scheduler.purgeExpiredKeys();

        verify(idempotencyService, times(1)).purgeExpiredKeysInternal();
    }

    @Test
    void execute_shouldDelegateToService() {
        scheduler.execute();

        verify(idempotencyService, times(1)).purgeExpiredKeysInternal();
    }

    @Test
    void getName_shouldReturnCorrectName() {
        String name = scheduler.getName();

        org.assertj.core.api.Assertions.assertThat(name).isEqualTo("IdempotencyPurgeScheduler");
    }

    @Test
    void getCronExpression_shouldReturnMidnightCron() {
        String cronExpression = scheduler.getCronExpression();

        org.assertj.core.api.Assertions.assertThat(cronExpression).isEqualTo("0 0 0 * * *");
    }
}
