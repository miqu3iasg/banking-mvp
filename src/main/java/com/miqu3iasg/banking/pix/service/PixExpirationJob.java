package com.miqu3iasg.banking.pix.service;

import com.miqu3iasg.banking.pix.metrics.PixMetrics;
import com.miqu3iasg.banking.pix.repository.PixChargeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@ConditionalOnProperty(name = "efi.webclient.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PixExpirationJob {
    private final PixChargeRepository chargeRepository;
    private final PixMetrics pixMetrics;

    @Transactional
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @SchedulerLock(name = "pixExpirationJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT5S")
    public void expireCharges() {
        log.info("Starting PIX charge expiration job");

        var expiredCharges = chargeRepository.findExpiredPendingCharges(Instant.now());

        expiredCharges.forEach(charge -> {
            charge.expire();

            log.info("Expired PIX charge: id={} accountId={} amount={}",
                    charge.getId(),
                    charge.getAccountId(),
                    charge.getAmount());
        });

        chargeRepository.saveAll(expiredCharges);

        var expiredCount = expiredCharges.size();

        pixMetrics.recordChargesExpired(expiredCount);

        log.info("Finished PIX charge expiration job, expiredCount={}", expiredCount);
    }
}
