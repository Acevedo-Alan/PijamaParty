package com.pijamaparty.store.promotions.adapter.out.activity;

import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivityLog;
import com.pijamaparty.store.promotions.application.CouponActivity;
import org.springframework.stereotype.Component;

/** Records the back-office changes of coupons through the activity module's public API, in the caller's transaction (UC-37). */
@Component
class ActivityLogCouponActivity implements CouponActivity {

    private final ActivityLog activityLog;

    ActivityLogCouponActivity(ActivityLog activityLog) {
        this.activityLog = activityLog;
    }

    @Override
    public void record(ActivityEntry entry) {
        activityLog.record(entry);
    }
}
