package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityEntry;

/**
 * Outbound port: records a back-office change of the catalog or the home page in the activity log (UC-37), in the
 * caller's transaction, after the change itself was written. A use case that changed nothing records nothing.
 */
@FunctionalInterface
public interface CatalogActivity {

    void record(ActivityEntry entry);
}
