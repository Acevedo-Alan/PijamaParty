package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.HomeBanner;
import java.time.Instant;
import java.util.Optional;

/** Fake of {@link LoadHomeBanner} and {@link SaveHomeBanner}: starts hidden and empty, as V39 creates the row. */
public class InMemoryHomeBannerStore implements LoadHomeBanner, SaveHomeBanner {

    public static final Instant CREATED_AT = Instant.parse("2026-10-19T00:00:00Z");

    private HomeBanner stored = HomeBanner.restore(Optional.empty(), Optional.empty(), false, CREATED_AT);

    @Override
    public synchronized HomeBanner load() {
        return stored.copy();
    }

    @Override
    public synchronized HomeBanner lock() {
        return stored.copy();
    }

    @Override
    public synchronized void save(HomeBanner banner) {
        stored = banner.copy();
    }
}
