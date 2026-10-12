package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.VariantId;
import java.util.Collection;
import java.util.Map;

/**
 * Outbound read port: what a cart line shows of its variant (UC-08), whether the shop still sells it or not, within
 * the caller's transaction.
 */
@FunctionalInterface
public interface ItemDescriptions {

    /** The description of each variant asked for that the catalog has; unknown variants are left out. */
    Map<VariantId, ItemDescription> describe(Collection<VariantId> variantIds);
}
