package com.pijamaparty.store.activity.application;

import com.pijamaparty.store.activity.api.ActivityArea;
import com.pijamaparty.store.platform.domain.Actor;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-37 (T-E8-01): the "Actividad" screen lists the entries newest first ({@code GET /api/admin/activity}), every area
 * or one, between two store dates if given.
 */
public interface ListActivity {

    int PAGE_SIZE = 50;

    Result execute(Query query);

    /**
     * @param from inclusive, a store date; no lower bound when empty
     * @param to inclusive, a store date; no upper bound when empty
     * @param page from 0
     */
    record Query(Actor actor, Optional<ActivityArea> area, Optional<LocalDate> from, Optional<LocalDate> to,
                 int page) {

        public Query {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(area, "area");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            if (page < 0) {
                throw new IllegalArgumentException("page must not be negative");
            }
        }
    }

    sealed interface Result {

        /** @param entries the page asked for; empty past the last one */
        record Listed(List<RecordedActivity> entries, boolean hasMore) implements Result {

            public Listed {
                entries = List.copyOf(entries);
            }
        }

        /** {@code from} is after {@code to}. */
        record InvalidPeriod() implements Result {}

        record Forbidden() implements Result {}
    }
}
