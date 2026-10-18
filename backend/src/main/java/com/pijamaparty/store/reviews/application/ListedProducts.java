package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Optional;

/**
 * Outbound read port (UC-24), backed by {@code catalog.api}: the product a visitor sees at a slug, in the caller's
 * transaction.
 */
public interface ListedProducts {

    /** Empty when no product has the slug or visitors do not see it, as the product page. */
    Optional<ProductId> bySlug(String slug);
}
