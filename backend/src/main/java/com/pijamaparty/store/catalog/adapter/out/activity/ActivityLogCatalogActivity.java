package com.pijamaparty.store.catalog.adapter.out.activity;

import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivityLog;
import com.pijamaparty.store.catalog.application.CatalogActivity;
import org.springframework.stereotype.Component;

/** Records catalog changes through the activity module's public API, in the caller's transaction (UC-37). */
@Component
class ActivityLogCatalogActivity implements CatalogActivity {

    private final ActivityLog activityLog;

    ActivityLogCatalogActivity(ActivityLog activityLog) {
        this.activityLog = activityLog;
    }

    @Override
    public void record(ActivityEntry entry) {
        activityLog.record(entry);
    }
}
