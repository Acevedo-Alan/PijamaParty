package com.pijamaparty.store.catalog.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A path of this store, where a link of the {@link HomeBanner} goes (UC-32): exactly one leading {@code /}, so no
 * scheme and no host, no backslash (browsers read {@code /\host} as {@code //host}) and no control characters, at
 * most 200 characters. The banner can never send shoppers to another site.
 */
public record StorePath(String value) {

    public static final int MAX_LENGTH = 200;

    public StorePath {
        Objects.requireNonNull(value, "value");
        if (!isValid(value)) {
            throw new IllegalArgumentException("not a path of this store");
        }
    }

    /** Trims what the administrator typed; empty when it is not a path of this store. */
    public static Optional<StorePath> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        return isValid(trimmed) ? Optional.of(new StorePath(trimmed)) : Optional.empty();
    }

    private static boolean isValid(String value) {
        return value.startsWith("/") && !value.startsWith("//") && value.indexOf('\\') < 0
            && PlainText.isSingleLine(value, MAX_LENGTH);
    }
}
