package com.miqu3iasg.banking.boleto.scheduler;

import com.miqu3iasg.banking.boleto.metrics.BoletoMetrics;
import com.miqu3iasg.banking.boleto.repository.BoletoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Daily scheduled job that transitions overdue PENDING boleto to EXPIRED.
 * <p>
 * Runs once per day at 01:00 America/Sao_Paulo to process boleto whose due date
 * has passed without payment confirmation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoletoExpirationScheduler {

	private final BoletoRepository boletoRepository;
	private final BoletoMetrics metrics;
	// TODO: make the two schedulers in the same standard

	@Transactional
	@Scheduled(cron = "0 0 1 * * *", zone = "America/Sao_Paulo")
	public void expireOverdueBoletos () {
		var today = LocalDate.now();
		log.info("Boleto expiration job started for date={}", today);

		var overdue = boletoRepository.findAllPendingOverdue(today);

		if (overdue.isEmpty()) {
			log.info("Boleto expiration job completed: no overdue boleto found");
			return;
		}

		overdue.forEach(boleto -> {
			try {
				boleto.expire();

				boletoRepository.save(boleto);

			} catch (Exception e) {
				log.error("Failed to expire boleto id={} providerChargeId={}: {}",
					boleto.getId(),
					boleto.getProviderChargeId(),
					e.getMessage(), e);
			}
		});

		int expired = overdue.size();

		metrics.recordBoletosExpired(expired);

		log.info("Boleto expiration job completed: expired={} boleto", expired);
	}
}
