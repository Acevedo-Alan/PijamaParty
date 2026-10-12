package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;
import java.util.Optional;

/**
 * Outbound read port: whether the shop sells a variant now, at which price and with how many units available
 * (on hand − reserved), within the caller's transaction (UC-08).
 */
@FunctionalInterface
public interface VariantAvailability {

    /** Empty for a variant that is unknown or that the catalog does not sell. */
    Optional<SellableVariant> of(VariantId variantId);

    record SellableVariant(Money price, int available) {

        public SellableVariant {
            Objects.requireNonNull(price, "price");
            if (available < 0) {
                throw new IllegalArgumentException("available stock cannot be negative");
            }
        }
    }
}
