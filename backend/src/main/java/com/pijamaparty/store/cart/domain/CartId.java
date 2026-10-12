package com.pijamaparty.store.cart.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of a {@link Cart}. */
public record CartId(UUID value) {

    public CartId {
        Objects.requireNonNull(value, "value");
    }

    public static CartId of(UUID value) {
        return new CartId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
