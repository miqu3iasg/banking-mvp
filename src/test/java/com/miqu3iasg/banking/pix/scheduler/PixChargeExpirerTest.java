package com.miqu3iasg.banking.pix.scheduler;

import com.miqu3iasg.banking.pix.domain.PixCharge;
import com.miqu3iasg.banking.pix.repository.PixChargeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class PixChargeExpirerTest {

    private PixChargeRepository chargeRepository;
    private PixChargeExpirer expirer;

    @BeforeEach
    void setUp() {
        chargeRepository = mock(PixChargeRepository.class);
        expirer = new PixChargeExpirer(chargeRepository);
    }

    @Test
    void expireAndSave_whenChargeIsValid_expiresAndSaves() {
        PixCharge charge = mock(PixCharge.class);

        expirer.expireAndSave(charge);

        verify(charge).expire();
        verify(chargeRepository).save(charge);
    }
}
