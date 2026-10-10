package com.pijamaparty.store.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of a {@link Category}. */
public record CategoryId(UUID value) {

    public CategoryId {
        Objects.requireNonNull(value, "value");
    }

    public static CategoryId of(UUID value) {
        return new CategoryId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
