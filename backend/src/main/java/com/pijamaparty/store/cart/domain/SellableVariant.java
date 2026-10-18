package com.pijamaparty.store.cart.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * A variant the shop sells now: its current price, how many units are available (on hand − reserved), and the
 * catalog's product and category it belongs to, which a coupon's scope is compared with (UC-23).
 */
public record SellableVariant(Money price, int available, UUID productId, UUID categoryId) {

    public SellableVariant {
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(categoryId, "categoryId");
        if (available < 0) {
            throw new IllegalArgumentException("available stock cannot be negative");
        }
    }
}
