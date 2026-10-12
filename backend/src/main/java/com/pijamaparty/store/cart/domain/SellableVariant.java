package com.pijamaparty.store.cart.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;

/** A variant the shop sells now: its current price and how many units are available (on hand − reserved). */
public record SellableVariant(Money price, int available) {

    public SellableVariant {
        Objects.requireNonNull(price, "price");
        if (available < 0) {
            throw new IllegalArgumentException("available stock cannot be negative");
        }
    }
}
