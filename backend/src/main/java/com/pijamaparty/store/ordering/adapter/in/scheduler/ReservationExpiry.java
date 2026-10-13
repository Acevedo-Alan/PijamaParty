package com.pijamaparty.store.ordering.adapter.in.scheduler;

import com.pijamaparty.store.ordering.application.ExpireOverdueOrder;
import com.pijamaparty.store.platform.config.TimeConfiguration;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UC-16: every minute ({@code store.ordering.reservation-expiry.cron}) the orders awaiting payment whose reservation
 * expired become {@code EXPIRED} and release their stock, one transaction each, at most {@link #MAX_PER_RUN} per run.
 */
@Component
class ReservationExpiry {

    /** Far above what a minute brings at fewer than 20 orders a day; the rest wait for the next run. */
    static final int MAX_PER_RUN = 100;

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiry.class);

    private final ExpireOverdueOrder expireOverdueOrder;

    ReservationExpiry(ExpireOverdueOrder expireOverdueOrder) {
        this.expireOverdueOrder = expireOverdueOrder;
    }

    @Scheduled(cron = "${store.ordering.reservation-expiry.cron}", zone = TimeConfiguration.STORE_ZONE_ID)
    void run() {
        int expired = 0;
        while (expired < MAX_PER_RUN
            && CurrentActor.callAsSystem(expireOverdueOrder::execute) instanceof ExpireOverdueOrder.Result.Expired) {
            expired++;
        }
        if (expired > 0) {
            log.info("Reservation expiry expired {} orders", expired);
        }
    }
}
