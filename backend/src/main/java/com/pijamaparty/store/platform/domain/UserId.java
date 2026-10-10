package com.pijamaparty.store.platform.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of a registered user (customer or administrator). Lives in the shared kernel because {@link Actor} needs it. */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
