package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.activity.api.ActivityEntry;

/**
 * Outbound port: records the moderation of reviews in the activity log (UC-37), in the caller's transaction, after the change itself was
 * written. A use case that changed nothing records nothing.
 */
@FunctionalInterface
public interface ReviewActivity {

    void record(ActivityEntry entry);
}
