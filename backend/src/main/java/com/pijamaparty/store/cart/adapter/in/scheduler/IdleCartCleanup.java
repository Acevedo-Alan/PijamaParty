package com.pijamaparty.store.cart.adapter.in.scheduler;

import com.pijamaparty.store.cart.application.RemoveIdleCarts;
import com.pijamaparty.store.platform.config.TimeConfiguration;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** T-E2-04: every day ({@code store.cart.cleanup.cron}, store time) deletes the carts idle for more than 30 days. */
@Component
class IdleCartCleanup {

    private static final Logger log = LoggerFactory.getLogger(IdleCartCleanup.class);

    private final RemoveIdleCarts removeIdleCarts;

    IdleCartCleanup(RemoveIdleCarts removeIdleCarts) {
        this.removeIdleCarts = removeIdleCarts;
    }

    @Scheduled(cron = "${store.cart.cleanup.cron}", zone = TimeConfiguration.STORE_ZONE_ID)
    void run() {
        RemoveIdleCarts.Result result = CurrentActor.callAsSystem(removeIdleCarts::execute);
        log.info("Idle cart cleanup deleted {} carts", result.removed());
    }
}
