package com.pijamaparty.store.settings.application;

import com.pijamaparty.store.settings.domain.LowStockThreshold;
import com.pijamaparty.store.settings.domain.StoreSettings;
import java.time.Instant;
import java.util.Optional;

/** Fake of {@link LoadStoreSettings} and {@link SaveStoreSettings}: one row in memory, as the migration creates it. */
public class InMemoryStoreSettingsStore implements LoadStoreSettings, SaveStoreSettings {

    /** What V42 creates, without the values of the business (D-15). */
    public static final StoreSettings EMPTY = new StoreSettings(Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
        LowStockThreshold.DEFAULT, 0);

    private StoreSettings stored = EMPTY;
    private Instant updatedAt;

    @Override
    public synchronized StoreSettings load() {
        return stored;
    }

    @Override
    public synchronized boolean save(StoreSettings settings, Instant at) {
        if (stored.version() != settings.version() - 1) {
            return false;
        }
        stored = settings;
        updatedAt = at;
        return true;
    }

    /** As if another save got in: the stored settings without the version check. */
    public synchronized void replace(StoreSettings settings) {
        stored = settings;
    }

    public synchronized Instant updatedAt() {
        return updatedAt;
    }
}
