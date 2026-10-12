package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.SellableVariant;
import com.pijamaparty.store.cart.domain.VariantId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outbound read port: which variants the shop sells now, at which price and with how many units available
 * (on hand − reserved), within the caller's transaction (UC-08, UC-09).
 */
@FunctionalInterface
public interface VariantAvailability {

    /** Each variant asked for that the shop sells; unknown variants and those the catalog does not sell are left out. */
    Map<VariantId, SellableVariant> sellable(Collection<VariantId> variantIds);

    /** Empty for a variant that is unknown or that the catalog does not sell. */
    default Optional<SellableVariant> of(VariantId variantId) {
        return Optional.ofNullable(sellable(List.of(variantId)).get(variantId));
    }
}
