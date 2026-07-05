package com.price.tracker.interfaces.scheduler;

import com.price.tracker.domain.service.check.PriceCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PriceCheckJob {

    private final PriceCheckService priceCheckService;

    @Scheduled(cron = "${tracker.check.cron}")
    public void runScheduledCheck() {
        priceCheckService.runCheckCycle();
    }
}
