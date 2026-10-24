package com.pijamaparty.store.activity.application;

import com.pijamaparty.store.activity.api.ActivityArea;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Outbound read port (UC-37): the entries of the "Actividad" screen, in the caller's transaction. */
public interface ActivityPages {

    /**
     * Up to {@code limit} entries, newest first (then by id), skipping the first {@code offset}.
     *
     * @param area every area when empty
     * @param from entries at or after it; no lower bound when empty
     * @param before entries before it; no upper bound when empty
     */
    List<RecordedActivity> page(Optional<ActivityArea> area, Optional<Instant> from, Optional<Instant> before,
                                int offset, int limit);
}
