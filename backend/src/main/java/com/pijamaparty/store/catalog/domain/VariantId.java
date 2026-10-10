package com.pijamaparty.store.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of a {@link Variant}. */
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
