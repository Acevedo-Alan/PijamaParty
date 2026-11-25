package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.SetupFacts.Named;

/** Outbound read port (UC-46): the catalog's gaps the store setup guide names. */
public interface CatalogSetupFacts {

    /** Active categories with a published product and no size guide, in the categories' order, at most {@code limit}. */
    Named categoriesWithoutSizeGuide(int limit);

    /** Published products without photos, newest first, at most {@code limit}. */
    Named publishedProductsWithoutPhoto(int limit);

    boolean homeBannerPublished();
}
