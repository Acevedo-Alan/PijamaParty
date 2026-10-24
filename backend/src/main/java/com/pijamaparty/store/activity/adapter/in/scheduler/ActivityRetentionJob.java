package com.pijamaparty.store.activity.adapter.in.scheduler;

import com.pijamaparty.store.activity.application.PurgeOldActivity;
import com.pijamaparty.store.platform.config.TimeConfiguration;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * D-E8-11 (T-E8-01): every day ({@code store.activity.retention.cron}, store time) deletes the activity entries older
 * than 2 years.
 */
@Component
class ActivityRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(ActivityRetentionJob.class);

    private final PurgeOldActivity purgeOldActivity;

    ActivityRetentionJob(PurgeOldActivity purgeOldActivity) {
        this.purgeOldActivity = purgeOldActivity;
    }

    @Scheduled(cron = "${store.activity.retention.cron}", zone = TimeConfiguration.STORE_ZONE_ID)
    void run() {
        PurgeOldActivity.Result result = CurrentActor.callAsSystem(purgeOldActivity::execute);
        log.info("Activity retention deleted {} entries", result.removed());
    }
}
