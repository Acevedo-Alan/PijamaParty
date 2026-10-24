package com.pijamaparty.store.shipping.adapter.out.activity;

import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivityLog;
import com.pijamaparty.store.shipping.application.ShippingActivity;
import org.springframework.stereotype.Component;

/** Records the back-office changes of shipping costs through the activity module's public API, in the caller's transaction (UC-37). */
@Component
class ActivityLogShippingActivity implements ShippingActivity {

    private final ActivityLog activityLog;

    ActivityLogShippingActivity(ActivityLog activityLog) {
        this.activityLog = activityLog;
    }

    @Override
    public void record(ActivityEntry entry) {
        activityLog.record(entry);
    }
}
