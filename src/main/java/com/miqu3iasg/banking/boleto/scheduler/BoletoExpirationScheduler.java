package com.miqu3iasg.banking.boleto.scheduler;

import com.miqu3iasg.banking.boleto.domain.Boleto;
import com.miqu3iasg.banking.boleto.metrics.BoletoMetrics;
import com.miqu3iasg.banking.boleto.repository.BoletoRepository;
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

import java.time.LocalDate;

/**
 * Daily scheduled job that transitions overdue PENDING boleto to EXPIRED.
 * <p>
 * Runs once per day at 02:00 UTC to process boleto whose due date
 * has passed without payment confirmation. Processes in batches to avoid memory issues.
 */
@Slf4j
@ConditionalOnProperty(name = "efi.webclient.enabled", havingValue = "true", matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class BoletoExpirationScheduler {

    private final BoletoRepository boletoRepository;
    private final BoletoMetrics metrics;

    private static final int PAGE_SIZE = 1000;

    @Transactional
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @SchedulerLock(name = "boletoExpirationJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT5S")
    public void expireOverdueBoletos() {
        var today = LocalDate.now();
        log.info("Boleto expiration job started for date={}", today);

        int page = 0;
        int totalExpired = 0;
        Page<Boleto> overduePage;
        Pageable pageable = PageRequest.of(page, PAGE_SIZE);

        do {
            overduePage = boletoRepository.findAllPendingOverdue(today, pageable);

            for (Boleto boleto : overduePage.getContent()) {
                try {
                    boleto.expire();
                    boletoRepository.save(boleto);
                } catch (Exception e) {
                    log.error("Failed to expire boleto id={} providerChargeId={}: {}",
                            boleto.getId(),
                            boleto.getProviderChargeId(),
                            e.getMessage(), e);
                }
            }

            int expired = overduePage.getNumberOfElements();
            totalExpired += expired;
            page++;
            pageable = PageRequest.of(page, PAGE_SIZE);

        } while (overduePage.hasNext());

        metrics.recordBoletosExpired(totalExpired);
        log.info("Boleto expiration job completed: expired={} boleto", totalExpired);
    }
}
