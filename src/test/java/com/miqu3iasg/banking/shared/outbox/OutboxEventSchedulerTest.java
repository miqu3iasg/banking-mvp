package com.miqu3iasg.banking.shared.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventSchedulerTest {

    @Mock
    private OutboxProcessor outboxProcessor;

    private OutboxEventScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxEventScheduler(outboxProcessor);
    }

    @Test
    void processOutboxEvents_shouldDelegateToProcessor() {
        scheduler.processOutboxEvents();

        verify(outboxProcessor, times(1)).poolAndProcess();
    }

    @Test
    void execute_shouldDelegateToProcessor() {
        scheduler.execute();

        verify(outboxProcessor, times(1)).poolAndProcess();
    }

    @Test
    void getName_shouldReturnCorrectName() {
        String name = scheduler.getName();

        org.assertj.core.api.Assertions.assertThat(name).isEqualTo("OutboxEventScheduler");
    }

    @Test
    void getCronExpression_shouldReturnFiveMinuteCron() {
        String cronExpression = scheduler.getCronExpression();

        org.assertj.core.api.Assertions.assertThat(cronExpression).isEqualTo("*/5 * * * * *");
    }
}
