package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.activity.api.ActivityEntry;

/**
 * Outbound port: records the back-office changes of shipping costs in the activity log (UC-37), in the caller's transaction, after the change itself was
 * written. A use case that changed nothing records nothing.
 */
@FunctionalInterface
public interface ShippingActivity {

    void record(ActivityEntry entry);
}
