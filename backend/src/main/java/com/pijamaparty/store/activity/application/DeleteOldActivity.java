package com.pijamaparty.store.activity.application;

import java.time.Instant;

/** Outbound port (D-E8-11): deletes the entries past their retention, in the caller's transaction. */
public interface DeleteOldActivity {

    /** Deletes every entry that occurred before {@code cutoff}, and answers how many. */
    int deleteOccurredBefore(Instant cutoff);
}
