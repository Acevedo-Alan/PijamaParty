package com.pijamaparty.store.catalog.api;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-25 (T-E5-08): the name and slug of products, published or not, so the back office can show and link the product
 * of a record that only keeps its id. Read only.
 */
public interface ProductLinks {

    /** The link of each product asked for; unknown products are left out. Joins the caller's transaction. */
    Map<UUID, ProductLink> linksOf(Collection<UUID> productIds);

    record ProductLink(String name, String slug) {

        public ProductLink {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(slug, "slug");
        }
    }
}
