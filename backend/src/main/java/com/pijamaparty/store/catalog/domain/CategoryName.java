package com.pijamaparty.store.catalog.domain;

import java.util.Objects;
import java.util.Optional;

/** The name of a {@link Category}: trimmed, 1 to 80 characters, and it must produce a slug of at most 100 (UC-05). */
public record CategoryName(String value) {

    /** 00-dominio-y-reglas.md, maximum lengths. */
    public static final int MAX_LENGTH = 80;
    public static final int MAX_SLUG_LENGTH = 100;

    public CategoryName {
        Objects.requireNonNull(value, "value");
        if (!isValid(value)) {
            throw new IllegalArgumentException("not a valid category name");
        }
    }

    /** Trims what the administrator typed; empty when it cannot be a category name. */
    public static Optional<CategoryName> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        return isValid(trimmed) ? Optional.of(new CategoryName(trimmed)) : Optional.empty();
    }

    /** UC-05: the slug is generated from the name. */
    public Slug slug() {
        return Slug.fromText(value, MAX_SLUG_LENGTH).orElseThrow();
    }

    private static boolean isValid(String value) {
        return PlainText.isSingleLine(value, MAX_LENGTH) && Slug.fromText(value, MAX_SLUG_LENGTH).isPresent();
    }
}
