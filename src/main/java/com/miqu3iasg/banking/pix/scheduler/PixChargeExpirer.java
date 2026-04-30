package com.miqu3iasg.banking.pix.scheduler;

import com.miqu3iasg.banking.pix.domain.PixCharge;
import com.miqu3iasg.banking.pix.repository.PixChargeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PixChargeExpirer {

    private final PixChargeRepository chargeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireAndSave(PixCharge charge) {
        charge.expire();
        chargeRepository.save(charge);
    }
}
