package com.pijamaparty.store.activity.application;

/** Outbound write port (UC-37): appends an entry, in the caller's transaction. */
public interface SaveActivity {

    void save(RecordedActivity activity);
}
