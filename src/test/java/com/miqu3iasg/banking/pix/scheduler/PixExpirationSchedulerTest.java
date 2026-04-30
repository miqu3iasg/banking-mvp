package com.miqu3iasg.banking.pix.scheduler;

import com.miqu3iasg.banking.pix.domain.PixCharge;
import com.miqu3iasg.banking.pix.metrics.PixMetrics;
import com.miqu3iasg.banking.pix.repository.PixChargeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PixExpirationSchedulerTest {

    private PixChargeRepository chargeRepository;
    private PixChargeExpirer chargeExpirer;
    private PixMetrics pixMetrics;
    private PixExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        chargeRepository = mock(PixChargeRepository.class);
        chargeExpirer = mock(PixChargeExpirer.class);
        pixMetrics = mock(PixMetrics.class);
        scheduler = new PixExpirationScheduler(chargeRepository, chargeExpirer, pixMetrics);
    }

    private PixCharge createExpiredCharge(UUID id) {
        PixCharge charge = mock(PixCharge.class);
        when(charge.getId()).thenReturn(id);
        when(charge.getAccountId()).thenReturn(UUID.randomUUID());
        when(charge.getAmount()).thenReturn(new BigDecimal("100.00"));
        return charge;
    }

    @Test
    void expireCharges_whenExpiredPendingChargesExist_delegatesToExpirer() {
        PixCharge expiredCharge = createExpiredCharge(UUID.randomUUID());
        Page<PixCharge> page = new PageImpl<>(List.of(expiredCharge));
        when(chargeRepository.findExpiredPendingCharges(any(Instant.class), any(Pageable.class)))
                .thenReturn(page)
                .thenReturn(new PageImpl<>(List.of()));

        scheduler.expireCharges();

        verify(chargeExpirer).expireAndSave(expiredCharge);
    }

    @Test
    void expireCharges_whenChargesExpired_recordsMetricsForSuccessfulExpirations() {
        PixCharge expiredCharge = createExpiredCharge(UUID.randomUUID());
        Page<PixCharge> page = new PageImpl<>(List.of(expiredCharge));
        when(chargeRepository.findExpiredPendingCharges(any(Instant.class), any(Pageable.class)))
                .thenReturn(page)
                .thenReturn(new PageImpl<>(List.of()));

        scheduler.expireCharges();

        verify(pixMetrics).recordChargesExpired(1);
    }

    @Test
    void expireCharges_whenExpirerFails_continuesProcessingAndCountsOnlySuccesses() {
        PixCharge failingCharge = createExpiredCharge(UUID.randomUUID());
        PixCharge validCharge = createExpiredCharge(UUID.randomUUID());

        Page<PixCharge> page = new PageImpl<>(List.of(failingCharge, validCharge));
        when(chargeRepository.findExpiredPendingCharges(any(Instant.class), any(Pageable.class)))
                .thenReturn(page)
                .thenReturn(new PageImpl<>(List.of()));
        doThrow(new RuntimeException("DB constraint violation")).when(chargeExpirer).expireAndSave(failingCharge);

        assertThatCode(() -> scheduler.expireCharges()).doesNotThrowAnyException();

        verify(chargeExpirer).expireAndSave(validCharge);
        verify(pixMetrics).recordChargesExpired(1);
    }

    @Test
    void expireCharges_whenMoreResultsExist_requestsNextPage() {
        PixCharge charge1 = createExpiredCharge(UUID.randomUUID());
        PixCharge charge2 = createExpiredCharge(UUID.randomUUID());

        Page<PixCharge> page1 = new PageImpl<>(List.of(charge1), PageRequest.of(0, 1), 2);
        Page<PixCharge> page2 = new PageImpl<>(List.of(charge2), PageRequest.of(1, 1), 2);

        when(chargeRepository.findExpiredPendingCharges(any(Instant.class), any(Pageable.class)))
                .thenReturn(page1)
                .thenReturn(page2);

        scheduler.expireCharges();

        verify(chargeRepository, times(2)).findExpiredPendingCharges(any(Instant.class), any(Pageable.class));
        verify(pixMetrics).recordChargesExpired(2);
    }

    @Test
    void expireCharges_whenNoExpiredCharges_recordsZeroMetrics() {
        when(chargeRepository.findExpiredPendingCharges(any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        scheduler.expireCharges();

        verify(pixMetrics).recordChargesExpired(0);
    }
}
