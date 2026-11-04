package com.pijamaparty.store.settings.application;

import com.pijamaparty.store.activity.api.ActivityEntry;

/**
 * Outbound port: records the changes of the store's settings in the activity log (UC-37), in the caller's
 * transaction, after the change itself was written. A save that changed nothing records nothing.
 */
@FunctionalInterface
public interface SettingsActivity {

    void record(ActivityEntry entry);
}
