package com.pijamaparty.store.inventory.api;

import java.util.Optional;
import java.util.UUID;

/**
 * The SKU of a variant, which names it in the activity log of a stock adjustment (UC-37). Declared here and
 * implemented by the catalog, which owns the variants and already depends on this module (ARCHITECTURE.md section 3,
 * rule 3). Joins the caller's transaction.
 */
public interface VariantSkus {

    /** Empty when the catalog has no such variant. */
    Optional<String> skuOf(UUID variantId);
}
