package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.Slug;
import com.pijamaparty.store.catalog.domain.VariantId;
import java.util.Optional;

/** Outbound port: reads products with their variants within the caller's transaction. */
public interface LoadProducts {

    Optional<Product> findById(ProductId id);

    /** UC-03: the product with this slug, published or not. */
    Optional<Product> findBySlug(Slug slug);

    /** For a write: also locks the product until the transaction ends, so writes to one product never interleave. */
    Optional<Product> lockById(ProductId id);

    /** The product of a variant, locked as {@link #lockById}. */
    Optional<Product> lockByVariantId(VariantId id);
}
