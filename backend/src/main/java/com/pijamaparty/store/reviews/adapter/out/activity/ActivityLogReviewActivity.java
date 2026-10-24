package com.pijamaparty.store.reviews.adapter.out.activity;

import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivityLog;
import com.pijamaparty.store.reviews.application.ReviewActivity;
import org.springframework.stereotype.Component;

/** Records the moderation of reviews through the activity module's public API, in the caller's transaction (UC-37). */
@Component
class ActivityLogReviewActivity implements ReviewActivity {

    private final ActivityLog activityLog;

    ActivityLogReviewActivity(ActivityLog activityLog) {
        this.activityLog = activityLog;
    }

    @Override
    public void record(ActivityEntry entry) {
        activityLog.record(entry);
    }
}
