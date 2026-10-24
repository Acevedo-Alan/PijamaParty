package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.activity.api.ActivityEntry;

/**
 * Outbound port: records stock adjustments in the activity log (UC-37), in the caller's transaction, after the change itself was
 * written. A use case that changed nothing records nothing.
 */
@FunctionalInterface
public interface StockActivity {

    void record(ActivityEntry entry);
}
