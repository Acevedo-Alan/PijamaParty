package com.pijamaparty.store.accounts.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of an {@link Address}. */
public record AddressId(UUID value) {

    public AddressId {
        Objects.requireNonNull(value, "value");
    }

    public static AddressId of(UUID value) {
        return new AddressId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
