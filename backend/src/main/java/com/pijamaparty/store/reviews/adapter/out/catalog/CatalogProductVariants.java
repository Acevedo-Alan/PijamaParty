package com.pijamaparty.store.reviews.adapter.out.catalog;

import com.pijamaparty.store.catalog.api.ProductVariantIds;
import com.pijamaparty.store.reviews.application.ProductVariants;
import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** {@link ProductVariants} through the public API of the catalog. */
@Component
class CatalogProductVariants implements ProductVariants {

    private final ProductVariantIds variantIds;

    CatalogProductVariants(ProductVariantIds variantIds) {
        this.variantIds = variantIds;
    }

    @Override
    public Optional<Set<UUID>> ofProduct(ProductId product) {
        return variantIds.ofProduct(product.value());
    }
}
