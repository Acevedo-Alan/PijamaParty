package com.pijamaparty.store.backoffice.adapter.out.catalog;

import com.pijamaparty.store.backoffice.application.CatalogSetupFacts;
import com.pijamaparty.store.backoffice.domain.SetupFacts.Named;
import com.pijamaparty.store.backoffice.domain.SetupFacts.Thing;
import com.pijamaparty.store.catalog.api.CatalogSetup;
import org.springframework.stereotype.Component;

/** Reads the catalog's gaps through its public API, in the caller's transaction (UC-46). */
@Component
class CatalogModuleSetupFacts implements CatalogSetupFacts {

    private final CatalogSetup catalogSetup;

    CatalogModuleSetupFacts(CatalogSetup catalogSetup) {
        this.catalogSetup = catalogSetup;
    }

    @Override
    public Named categoriesWithoutSizeGuide(int limit) {
        return named(catalogSetup.categoriesWithoutSizeGuide(limit));
    }

    @Override
    public Named publishedProductsWithoutPhoto(int limit) {
        return named(catalogSetup.publishedProductsWithoutPhoto(limit));
    }

    @Override
    public boolean homeBannerPublished() {
        return catalogSetup.homeBannerPublished();
    }

    private static Named named(CatalogSetup.Named named) {
        return new Named(named.first().stream().map(thing -> new Thing(thing.id(), thing.name())).toList(),
            named.total());
    }
}
