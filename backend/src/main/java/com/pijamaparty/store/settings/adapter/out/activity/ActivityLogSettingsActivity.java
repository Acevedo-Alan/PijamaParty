package com.pijamaparty.store.settings.adapter.out.activity;

import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivityLog;
import com.pijamaparty.store.settings.application.SettingsActivity;
import org.springframework.stereotype.Component;

/** Records the changes of the store's settings through the activity module's public API, in the caller's transaction (UC-37). */
@Component
class ActivityLogSettingsActivity implements SettingsActivity {

    private final ActivityLog activityLog;

    ActivityLogSettingsActivity(ActivityLog activityLog) {
        this.activityLog = activityLog;
    }

    @Override
    public void record(ActivityEntry entry) {
        activityLog.record(entry);
    }
}
