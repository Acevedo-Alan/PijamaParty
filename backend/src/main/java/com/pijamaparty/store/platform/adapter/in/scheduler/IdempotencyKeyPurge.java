package com.pijamaparty.store.platform.adapter.in.scheduler;

import com.pijamaparty.store.platform.application.PurgeIdempotencyKeys;
import com.pijamaparty.store.platform.config.TimeConfiguration;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * T-E3-14 (RNF-20.2): every day ({@code store.platform.idempotency-purge.cron}, store time) deletes the idempotency
 * keys past their 24 hours.
 */
@Component
class IdempotencyKeyPurge {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyPurge.class);

    private final PurgeIdempotencyKeys purgeIdempotencyKeys;

    IdempotencyKeyPurge(PurgeIdempotencyKeys purgeIdempotencyKeys) {
        this.purgeIdempotencyKeys = purgeIdempotencyKeys;
    }

    @Scheduled(cron = "${store.platform.idempotency-purge.cron}", zone = TimeConfiguration.STORE_ZONE_ID)
    void run() {
        PurgeIdempotencyKeys.Result result = CurrentActor.callAsSystem(purgeIdempotencyKeys::execute);
        log.info("Idempotency key purge deleted {} keys", result.removed());
    }
}
