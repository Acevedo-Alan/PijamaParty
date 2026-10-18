package com.pijamaparty.store.reviews.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of a {@link Review}. */
public record ReviewId(UUID value) {

    public ReviewId {
        Objects.requireNonNull(value, "value");
    }

    public static ReviewId of(UUID value) {
        return new ReviewId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
