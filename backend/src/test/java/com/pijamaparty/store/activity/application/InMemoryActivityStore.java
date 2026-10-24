package com.pijamaparty.store.activity.application;

import com.pijamaparty.store.activity.api.ActivityArea;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Fake of the activity log: entries are immutable records, kept in a list. */
public class InMemoryActivityStore implements SaveActivity, ActivityPages, DeleteOldActivity {

    private final List<RecordedActivity> entries = new ArrayList<>();

    @Override
    public synchronized void save(RecordedActivity activity) {
        entries.add(activity);
    }

    @Override
    public synchronized List<RecordedActivity> page(Optional<ActivityArea> area, Optional<Instant> from,
                                                    Optional<Instant> before, int offset, int limit) {
        return entries.stream()
            .filter(e -> area.isEmpty() || e.entry().action().area() == area.get())
            .filter(e -> from.isEmpty() || !e.occurredAt().isBefore(from.get()))
            .filter(e -> before.isEmpty() || e.occurredAt().isBefore(before.get()))
            .sorted(Comparator.comparing(RecordedActivity::occurredAt).thenComparing(RecordedActivity::id).reversed())
            .skip(offset)
            .limit(limit)
            .toList();
    }

    @Override
    public synchronized int deleteOccurredBefore(Instant cutoff) {
        int before = entries.size();
        entries.removeIf(e -> e.occurredAt().isBefore(cutoff));
        return before - entries.size();
    }

    /** Every entry, in the order saved. */
    public synchronized List<RecordedActivity> all() {
        return List.copyOf(entries);
    }
}
