package com.pijamaparty.store.inventory.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of a {@link StockMovement}. */
public record MovementId(UUID value) {

    public MovementId {
        Objects.requireNonNull(value, "value");
    }

    public static MovementId of(UUID value) {
        return new MovementId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
