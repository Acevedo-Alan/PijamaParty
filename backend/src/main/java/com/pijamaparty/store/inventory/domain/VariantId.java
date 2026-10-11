package com.pijamaparty.store.inventory.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of a variant of the catalog, as the inventory knows it: the key of its {@link StockLevel}. */
public record VariantId(UUID value) {

    public VariantId {
        Objects.requireNonNull(value, "value");
    }

    public static VariantId of(UUID value) {
        return new VariantId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
