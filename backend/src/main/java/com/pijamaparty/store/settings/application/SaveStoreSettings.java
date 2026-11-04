package com.pijamaparty.store.settings.application;

import com.pijamaparty.store.settings.domain.StoreSettings;
import java.time.Instant;

/** Outbound port: writes the store's settings within the caller's transaction (UC-36). */
public interface SaveStoreSettings {

    /**
     * Stores {@code settings}, changed {@code at}, only if the stored ones are still the version before them.
     *
     * @return false when another save got in first; nothing is stored then
     */
    boolean save(StoreSettings settings, Instant at);
}
