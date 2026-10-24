package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.activity.api.ActivityEntry;

/**
 * Outbound port: records the back-office changes of orders and withdrawal requests in the activity log (UC-37), in the caller's transaction, after the change itself was
 * written. A use case that changed nothing records nothing.
 */
@FunctionalInterface
public interface OrderActivity {

    void record(ActivityEntry entry);
}
