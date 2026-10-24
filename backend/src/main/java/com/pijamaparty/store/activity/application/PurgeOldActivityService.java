package com.pijamaparty.store.activity.application;

import com.pijamaparty.store.activity.domain.ActivityRetention;
import java.time.Clock;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PurgeOldActivityService implements PurgeOldActivity {

    private final DeleteOldActivity deleteOldActivity;
    private final Clock clock;

    PurgeOldActivityService(DeleteOldActivity deleteOldActivity, Clock clock) {
        this.deleteOldActivity = deleteOldActivity;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute() {
        return new Result(deleteOldActivity.deleteOccurredBefore(ActivityRetention.cutoff(ZonedDateTime.now(clock))));
    }
}
