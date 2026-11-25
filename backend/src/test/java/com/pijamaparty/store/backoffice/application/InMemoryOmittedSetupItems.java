package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.SetupItem;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** Fake of {@link LoadOmittedSetupItems} and {@link SaveOmittedSetupItems}: a set of items, as the table's rows. */
public class InMemoryOmittedSetupItems implements LoadOmittedSetupItems, SaveOmittedSetupItems {

    private final Set<SetupItem> items = EnumSet.noneOf(SetupItem.class);

    @Override
    public Set<SetupItem> omitted() {
        return Set.copyOf(items);
    }

    @Override
    public void omit(SetupItem item, Instant at) {
        // As the table's check: only an item the guide lets omit can be stored.
        if (!item.omittable()) {
            throw new IllegalArgumentException(item + " cannot be omitted");
        }
        items.add(item);
    }

    @Override
    public void show(SetupItem item) {
        items.remove(item);
    }
}
