package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.HomeBanner;

/** Outbound port: reads the one {@link HomeBanner} within the caller's transaction (UC-32). It always exists (V39). */
public interface LoadHomeBanner {

    HomeBanner load();

    /** Like {@link #load()}, holding the banner's lock until the caller's transaction ends. */
    HomeBanner lock();
}
