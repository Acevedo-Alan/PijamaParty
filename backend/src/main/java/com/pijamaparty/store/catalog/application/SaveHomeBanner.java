package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.HomeBanner;

/** Outbound port: stores the {@link HomeBanner} within the caller's transaction, replacing the stored one (UC-32). */
public interface SaveHomeBanner {

    void save(HomeBanner banner);
}
