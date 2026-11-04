package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.activity.api.ActivityEntry;

/**
 * Outbound port: records what the back office's own screens do in the activity log (UC-37), such as opening a
 * customer's profile, in the caller's transaction.
 */
@FunctionalInterface
public interface BackofficeActivity {

    void record(ActivityEntry entry);
}
