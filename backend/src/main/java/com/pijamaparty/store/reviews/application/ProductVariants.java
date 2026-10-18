package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Outbound read port (UC-24), backed by {@code catalog.api}: the variants of a product, in the caller's transaction. */
public interface ProductVariants {

    /** The ids of every variant of the product, sold now or not; empty when there is no such product. */
    Optional<Set<UUID>> ofProduct(ProductId product);
}
