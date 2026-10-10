package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Product;

/**
 * Outbound port: writes a product and its variants within the caller's transaction. A product that would break a
 * database rule is not saved at all, and the caller's transaction stays usable.
 */
public interface SaveProduct {

    /** Inserts a new product. */
    Outcome add(Product product);

    /** Updates a stored product, inserting its new variants and updating the others (variants are never deleted). */
    Outcome update(Product product);

    enum Outcome {
        SAVED,
        /** Another product already has the slug. */
        SLUG_ALREADY_USED,
        /** Another variant already has the SKU of a new variant. */
        SKU_ALREADY_USED,
        /** The category does not exist (any more). */
        UNKNOWN_CATEGORY
    }
}
