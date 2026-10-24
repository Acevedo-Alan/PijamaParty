package com.pijamaparty.store.inventory.adapter.out.activity;

import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivityLog;
import com.pijamaparty.store.inventory.application.StockActivity;
import org.springframework.stereotype.Component;

/** Records stock adjustments through the activity module's public API, in the caller's transaction (UC-37). */
@Component
class ActivityLogStockActivity implements StockActivity {

    private final ActivityLog activityLog;

    ActivityLogStockActivity(ActivityLog activityLog) {
        this.activityLog = activityLog;
    }

    @Override
    public void record(ActivityEntry entry) {
        activityLog.record(entry);
    }
}
