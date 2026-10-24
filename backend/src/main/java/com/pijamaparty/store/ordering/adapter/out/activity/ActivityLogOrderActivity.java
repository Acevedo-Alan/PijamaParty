package com.pijamaparty.store.ordering.adapter.out.activity;

import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivityLog;
import com.pijamaparty.store.ordering.application.OrderActivity;
import org.springframework.stereotype.Component;

/** Records the back-office changes of orders and withdrawal requests through the activity module's public API, in the caller's transaction (UC-37). */
@Component
class ActivityLogOrderActivity implements OrderActivity {

    private final ActivityLog activityLog;

    ActivityLogOrderActivity(ActivityLog activityLog) {
        this.activityLog = activityLog;
    }

    @Override
    public void record(ActivityEntry entry) {
        activityLog.record(entry);
    }
}
