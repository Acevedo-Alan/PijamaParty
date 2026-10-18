package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Outbound read port (UC-25), backed by {@code catalog.api}: the name and slug of products, in the caller's
 * transaction.
 */
public interface ProductLinks {

    /** The link of each product asked for, published or not; unknown products are left out. */
    Map<ProductId, ProductLink> linksOf(Collection<ProductId> products);

    record ProductLink(String name, String slug) {

        public ProductLink {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(slug, "slug");
        }
    }
}
