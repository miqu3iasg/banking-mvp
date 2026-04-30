package com.miqu3iasg.banking.pix.service;

import com.miqu3iasg.banking.pix.domain.PixCharge;
import com.miqu3iasg.banking.pix.metrics.PixMetrics;
import com.miqu3iasg.banking.pix.repository.PixChargeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduled job that transitions overdue PENDING PIX charges to EXPIRED.
 * <p>
 * Runs once per day at 02:00 UTC to process charges whose expiresAt
 * timestamp has passed without payment confirmation. Processes in batches
 * to avoid memory issues. Each charge is expired and saved individually
 * so a failure on one charge does not roll back the entire batch.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "efi.webclient.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PixExpirationScheduler {

    private final PixChargeRepository chargeRepository;
    private final PixMetrics pixMetrics;

    private static final int PAGE_SIZE = 1000;

    @Transactional
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @SchedulerLock(name = "pixExpirationScheduler", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void expireCharges() {
        var now = Instant.now();
        log.info("PIX charge expiration job started for now={}", now);

        int page = 0;
        int totalExpired = 0;
        Page<PixCharge> expiredPage;
        Pageable pageable = PageRequest.of(page, PAGE_SIZE);

        do {
            expiredPage = chargeRepository.findExpiredPendingCharges(now, pageable);

            for (PixCharge charge : expiredPage.getContent()) {
                try {
                    charge.expire();
                    chargeRepository.save(charge);
                    log.info("Expired PIX charge: id={} accountId={} amount={}",
                            charge.getId(),
                            charge.getAccountId(),
                            charge.getAmount());
                } catch (Exception e) {
                    log.error("Failed to expire PIX charge id={}: {}",
                            charge.getId(), e.getMessage(), e);
                }
            }

            int expired = expiredPage.getNumberOfElements();
            totalExpired += expired;
            page++;
            pageable = PageRequest.of(page, PAGE_SIZE);

        } while (expiredPage.hasNext());

        pixMetrics.recordChargesExpired(totalExpired);
        log.info("PIX charge expiration job completed: expired={} charges", totalExpired);
    }
}
