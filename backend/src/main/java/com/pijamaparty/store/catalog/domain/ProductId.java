package com.pijamaparty.store.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of a {@link Product}. */
public record ProductId(UUID value) {

    public ProductId {
        Objects.requireNonNull(value, "value");
    }

    public static ProductId of(UUID value) {
        return new ProductId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
