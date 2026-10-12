package com.pijamaparty.store.cart.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of a catalog variant, as the cart refers to it (no foreign key, ARCHITECTURE.md section 3, rule 6). */
public record VariantId(UUID value) {

    public VariantId {
        Objects.requireNonNull(value, "value");
    }

    public static VariantId of(UUID value) {
        return new VariantId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
