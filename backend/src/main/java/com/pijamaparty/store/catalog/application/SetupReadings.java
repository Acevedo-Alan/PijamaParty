package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.api.CatalogSetup.Named;

/** Outbound read port (UC-46): what the store setup guide needs of the catalog tables. */
public interface SetupReadings {

    Named categoriesWithoutSizeGuide(int limit);

    Named publishedProductsWithoutPhoto(int limit);

    boolean homeBannerPublished();
}
