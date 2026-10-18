package com.pijamaparty.store.reviews.adapter.out.catalog;

import com.pijamaparty.store.catalog.api.ListedProductIds;
import com.pijamaparty.store.reviews.application.ListedProducts;
import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** {@link ListedProducts} through the public API of the catalog. */
@Component
class CatalogListedProducts implements ListedProducts {

    private final ListedProductIds listedProductIds;

    CatalogListedProducts(ListedProductIds listedProductIds) {
        this.listedProductIds = listedProductIds;
    }

    @Override
    public Optional<ProductId> bySlug(String slug) {
        return listedProductIds.listedProductId(slug).map(ProductId::of);
    }
}
