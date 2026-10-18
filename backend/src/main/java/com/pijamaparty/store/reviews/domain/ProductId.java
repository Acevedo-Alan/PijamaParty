package com.pijamaparty.store.reviews.domain;

import java.util.Objects;
import java.util.UUID;

/** The catalog product a {@link Review} is about; refers to the catalog module, without a foreign key (V37). */
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
