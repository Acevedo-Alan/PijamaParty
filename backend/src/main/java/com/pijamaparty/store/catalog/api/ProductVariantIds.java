package com.pijamaparty.store.catalog.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * UC-24 (T-E5-06): the variants of a product, sold now or not, so a review can be matched with the orders that bought
 * it. Read only, in the caller's transaction.
 */
public interface ProductVariantIds {

    /** The ids of every variant of the product, also hidden or inactive ones; empty when there is no such product. */
    Optional<Set<UUID>> ofProduct(UUID productId);
}
