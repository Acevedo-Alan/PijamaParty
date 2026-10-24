package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityEntry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake of {@link CatalogActivity}: keeps the entries in the order they were recorded. */
public class InMemoryCatalogActivity implements CatalogActivity {

    private final List<ActivityEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void record(ActivityEntry entry) {
        entries.add(entry);
    }

    public List<ActivityEntry> entries() {
        return List.copyOf(entries);
    }

    /** The only entry recorded; fails when there is none or more than one. */
    public ActivityEntry single() {
        if (entries.size() != 1) {
            throw new AssertionError("expected one activity entry, found " + entries);
        }
        return entries.getFirst();
    }

    public void clear() {
        entries.clear();
    }
}
