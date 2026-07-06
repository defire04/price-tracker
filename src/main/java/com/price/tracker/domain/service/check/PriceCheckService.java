package com.price.tracker.domain.service.check;

public interface PriceCheckService {

    void runCheckCycle();

    /**
     * On-demand check of a single tracking. Returns false when the fetch or
     * parse failed (the admin alert is already sent inside).
     */
    boolean checkItemNow(long itemId);
}
