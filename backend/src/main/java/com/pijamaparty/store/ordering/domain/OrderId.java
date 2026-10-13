package com.pijamaparty.store.ordering.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of an order. */
public record OrderId(UUID value) {

    public OrderId {
        Objects.requireNonNull(value, "value");
    }

    public static OrderId of(UUID value) {
        return new OrderId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
