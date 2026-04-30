package com.miqu3iasg.banking.pix.scheduler;

import com.miqu3iasg.banking.pix.domain.PixCharge;
import com.miqu3iasg.banking.pix.domain.PixChargeStatus;
import com.miqu3iasg.banking.pix.repository.PixChargeRepository;
import com.miqu3iasg.banking.pix.service.PixExpirationScheduler;
import com.miqu3iasg.banking_mvp.shared.support.AbstractIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PixExpirationSchedulerIT extends AbstractIntegrationTestSupport {

    @Autowired
    private PixChargeRepository chargeRepository;

    @Autowired
    private PixExpirationScheduler scheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        chargeRepository.deleteAll();
    }

    private UUID insertExpiredCharge() {
        var accountId = openChecking(CPF_1).id();
        var txid = "txid-expired-" + UUID.randomUUID();
        var now = Instant.now();

        PixCharge charge = PixCharge.create(
                accountId,
                new BigDecimal("100.00"),
                "Test User",
                "52998224725",
                txid,
                3600
        );
        charge.enrichWithProviderData(txid, "qr-code", "copy-paste");
        charge = chargeRepository.save(charge);

        jdbcTemplate.update(
                "UPDATE pix_charges SET expires_at = ? WHERE id = ?",
                now.minusSeconds(3600), charge.getId()
        );

        return charge.getId();
    }

    private UUID insertValidPendingCharge() {
        var accountId = openChecking(CPF_2).id();
        var txid = "txid-valid-" + UUID.randomUUID();

        PixCharge charge = PixCharge.create(
                accountId,
                new BigDecimal("50.00"),
                "Test User",
                "87748248800",
                txid,
                86400
        );
        charge.enrichWithProviderData(txid, "qr-code", "copy-paste");
        chargeRepository.save(charge);

        return charge.getId();
    }

    @Test
    void expireCharges_transitionsExpiredChargesToExpired() {
        UUID expiredChargeId = insertExpiredCharge();

        scheduler.expireCharges();

        PixCharge updated = chargeRepository.findById(expiredChargeId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PixChargeStatus.EXPIRED);
    }

    @Test
    void expireCharges_keepsNonExpiredChargesPending() {
        UUID validChargeId = insertValidPendingCharge();

        scheduler.expireCharges();

        PixCharge updated = chargeRepository.findById(validChargeId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PixChargeStatus.PENDING);
    }

    @Test
    void expireCharges_handlesAlreadyExpiredChargeGracefully() {
        UUID expiredChargeId = insertExpiredCharge();

        scheduler.expireCharges();
        scheduler.expireCharges();

        PixCharge updated = chargeRepository.findById(expiredChargeId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PixChargeStatus.EXPIRED);
    }
}
