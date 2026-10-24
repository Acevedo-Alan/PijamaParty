package com.pijamaparty.store.activity.application;

import com.pijamaparty.store.activity.api.ActivityEntry;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** UC-37: an entry of the log as stored, within the limits of {@code ActivityLimits}. */
public record RecordedActivity(UUID id, Instant occurredAt, ActivityEntry entry) {

    public RecordedActivity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(entry, "entry");
    }
}
