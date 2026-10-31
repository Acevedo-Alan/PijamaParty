package com.pijamaparty.store.catalog.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-33 (and the Stock screen of T-E8-09): the active variants of published products whose available stock (on hand
 * minus reserved, UC-07) is at or under a threshold. Read only, in the caller's transaction or its own.
 */
public interface LowStockVariants {

    /** Sold-out ones first, then by available stock, product name, size and color position. */
    List<LowStockVariant> list(int threshold, int limit);

    long count(int threshold);

    record LowStockVariant(UUID variantId, UUID productId, String productName, String size, String color,
                           long available) {

        public LowStockVariant {
            Objects.requireNonNull(variantId, "variantId");
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(productName, "productName");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(color, "color");
        }
    }
}
