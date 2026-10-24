package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.activity.api.ActivityEntry;

/**
 * Outbound port: records the administrator's sign-ins in the activity log (UC-37), in the caller's transaction, after the change itself was
 * written. A use case that changed nothing records nothing.
 */
@FunctionalInterface
public interface AccountActivity {

    void record(ActivityEntry entry);
}
