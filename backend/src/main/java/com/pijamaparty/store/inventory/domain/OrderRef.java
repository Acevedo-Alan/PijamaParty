package com.pijamaparty.store.inventory.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of an order of the ordering module, as the inventory knows it: the owner of a reservation (UC-16). */
public record OrderRef(UUID value) {

    public OrderRef {
        Objects.requireNonNull(value, "value");
    }

    public static OrderRef of(UUID value) {
        return new OrderRef(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
