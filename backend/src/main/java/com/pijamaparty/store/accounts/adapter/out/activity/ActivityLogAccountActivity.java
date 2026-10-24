package com.pijamaparty.store.accounts.adapter.out.activity;

import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivityLog;
import com.pijamaparty.store.accounts.application.AccountActivity;
import org.springframework.stereotype.Component;

/** Records the administrator's sign-ins through the activity module's public API, in the caller's transaction (UC-37). */
@Component
class ActivityLogAccountActivity implements AccountActivity {

    private final ActivityLog activityLog;

    ActivityLogAccountActivity(ActivityLog activityLog) {
        this.activityLog = activityLog;
    }

    @Override
    public void record(ActivityEntry entry) {
        activityLog.record(entry);
    }
}
