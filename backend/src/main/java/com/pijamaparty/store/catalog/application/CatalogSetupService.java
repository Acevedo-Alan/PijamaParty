package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.api.CatalogSetup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-46: the catalog's part of the store setup guide, read through {@link SetupReadings}. */
@Service
class CatalogSetupService implements CatalogSetup {

    private final SetupReadings readings;

    CatalogSetupService(SetupReadings readings) {
        this.readings = readings;
    }

    @Override
    @Transactional(readOnly = true)
    public Named categoriesWithoutSizeGuide(int limit) {
        return readings.categoriesWithoutSizeGuide(limit);
    }

    @Override
    @Transactional(readOnly = true)
    public Named publishedProductsWithoutPhoto(int limit) {
        return readings.publishedProductsWithoutPhoto(limit);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean homeBannerPublished() {
        return readings.homeBannerPublished();
    }
}
