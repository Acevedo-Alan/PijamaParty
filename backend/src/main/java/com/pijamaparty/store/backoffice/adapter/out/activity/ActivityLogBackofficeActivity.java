package com.pijamaparty.store.backoffice.adapter.out.activity;

import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivityLog;
import com.pijamaparty.store.backoffice.application.BackofficeActivity;
import org.springframework.stereotype.Component;

/** Records what the back office's screens do through the activity module's public API, in the caller's transaction (UC-37). */
@Component
class ActivityLogBackofficeActivity implements BackofficeActivity {

    private final ActivityLog activityLog;

    ActivityLogBackofficeActivity(ActivityLog activityLog) {
        this.activityLog = activityLog;
    }

    @Override
    public void record(ActivityEntry entry) {
        activityLog.record(entry);
    }
}
