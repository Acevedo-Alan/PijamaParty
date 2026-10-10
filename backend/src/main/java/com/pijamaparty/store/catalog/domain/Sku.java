package com.pijamaparty.store.catalog.domain;

import java.util.Objects;

/**
 * The stock-keeping unit of a {@link Variant}, unique across the shop (UC-06). The system generates it and it never
 * changes; this type only checks what the database does (V3: trimmed, 1 to 40 characters).
 */
public record Sku(String value) {

    public static final int MAX_LENGTH = 40;

    public Sku {
        Objects.requireNonNull(value, "value");
        if (!PlainText.isSingleLine(value, MAX_LENGTH)) {
            throw new IllegalArgumentException("not a valid SKU");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
