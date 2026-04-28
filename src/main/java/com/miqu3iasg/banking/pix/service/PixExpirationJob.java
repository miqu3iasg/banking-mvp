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
 * Expires PIX charges that are past their expiresAt timestamp and still PENDING.
 * <p>
 * Runs daily at 02:00 UTC to process expired charges in batches to avoid memory issues.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "efi.webclient.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PixExpirationJob {

    private final PixChargeRepository chargeRepository;
    private final PixMetrics pixMetrics;

    private static final int PAGE_SIZE = 1000;

    @Transactional
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @SchedulerLock(name = "pixExpirationJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT5S")
    public void expireCharges() {
        log.info("Starting PIX charge expiration job");

        int page = 0;
        int totalProcessed = 0;
        Page<PixCharge> expiredPage;
        Pageable pageable = PageRequest.of(page, PAGE_SIZE);

        do {
            expiredPage = chargeRepository.findExpiredPendingCharges(Instant.now(), pageable);

            for (PixCharge charge : expiredPage.getContent()) {
                charge.expire();
                log.info("Expired PIX charge: id={} accountId={} amount={}",
                        charge.getId(),
                        charge.getAccountId(),
                        charge.getAmount());
            }

            chargeRepository.saveAll(expiredPage.getContent());
            pixMetrics.recordChargesExpired(expiredPage.getNumberOfElements());
            totalProcessed += expiredPage.getNumberOfElements();
            page++;
            pageable = PageRequest.of(page, PAGE_SIZE);

        } while (expiredPage.hasNext());

        log.info("Finished PIX charge expiration job, totalProcessed={}", totalProcessed);
    }
}
