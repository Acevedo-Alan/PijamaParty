package com.pijamaparty.store.catalog.domain;

import java.util.Objects;
import java.util.Optional;

/** The description of a {@link Product}: trimmed, up to 5000 characters, line breaks allowed, possibly empty. */
public record Description(String value) {

    /** 00-dominio-y-reglas.md, maximum lengths. */
    public static final int MAX_LENGTH = 5000;

    public Description {
        Objects.requireNonNull(value, "value");
        if (!PlainText.isMultiLine(value, MAX_LENGTH)) {
            throw new IllegalArgumentException("not a valid description");
        }
    }

    /** Trims what the administrator typed; empty when it cannot be a description. */
    public static Optional<Description> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        return PlainText.isMultiLine(trimmed, MAX_LENGTH) ? Optional.of(new Description(trimmed)) : Optional.empty();
    }
}
