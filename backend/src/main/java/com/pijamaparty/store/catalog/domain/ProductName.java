package com.pijamaparty.store.catalog.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * The name of a {@link Product}: trimmed, 1 to 120 characters, one line, and it must produce a slug of at most 140
 * (UC-06, same rules as {@link CategoryName}).
 */
public record ProductName(String value) {

    /** 00-dominio-y-reglas.md, maximum lengths. */
    public static final int MAX_LENGTH = 120;
    public static final int MAX_SLUG_LENGTH = 140;

    public ProductName {
        Objects.requireNonNull(value, "value");
        if (!isValid(value)) {
            throw new IllegalArgumentException("not a valid product name");
        }
    }

    /** Trims what the administrator typed; empty when it cannot be a product name. */
    public static Optional<ProductName> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        return isValid(trimmed) ? Optional.of(new ProductName(trimmed)) : Optional.empty();
    }

    /** UC-06: the slug is generated from the name, as a category's. */
    public Slug slug() {
        return Slug.fromText(value, MAX_SLUG_LENGTH).orElseThrow();
    }

    private static boolean isValid(String value) {
        return PlainText.isSingleLine(value, MAX_LENGTH) && Slug.fromText(value, MAX_SLUG_LENGTH).isPresent();
    }
}
