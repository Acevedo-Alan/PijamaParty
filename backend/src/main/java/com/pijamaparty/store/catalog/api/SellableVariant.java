package com.pijamaparty.store.catalog.api;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * A variant the shop sells now (UC-08): its price, and its product and that product's category, which a coupon's
 * scope is compared with (UC-23).
 */
public record SellableVariant(Money price, UUID productId, UUID categoryId) {

    public SellableVariant {
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(categoryId, "categoryId");
    }
}
