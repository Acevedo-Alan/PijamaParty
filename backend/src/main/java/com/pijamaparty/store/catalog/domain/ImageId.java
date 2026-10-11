package com.pijamaparty.store.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of a {@link ProductImage}. */
public record ImageId(UUID value) {

    public ImageId {
        Objects.requireNonNull(value, "value");
    }

    public static ImageId of(UUID value) {
        return new ImageId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
