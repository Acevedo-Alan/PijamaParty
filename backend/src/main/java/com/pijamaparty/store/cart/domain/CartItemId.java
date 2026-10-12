package com.pijamaparty.store.cart.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of a {@link CartItem}. */
public record CartItemId(UUID value) {

    public CartItemId {
        Objects.requireNonNull(value, "value");
    }

    public static CartItemId of(UUID value) {
        return new CartItemId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
