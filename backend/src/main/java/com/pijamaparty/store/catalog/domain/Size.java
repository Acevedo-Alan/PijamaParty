package com.pijamaparty.store.catalog.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A size of a {@link Product}, free text shown in the order the administrator gave (UC-06): trimmed, 1 to 20
 * characters, one line (00-dominio-y-reglas.md, maximum lengths).
 */
public record Size(String value) {

    public static final int MAX_LENGTH = 20;

    public Size {
        Objects.requireNonNull(value, "value");
        if (!PlainText.isSingleLine(value, MAX_LENGTH)) {
            throw new IllegalArgumentException("not a valid size");
        }
    }

    /** Trims what the administrator typed; empty when it cannot be a size. */
    public static Optional<Size> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        return PlainText.isSingleLine(trimmed, MAX_LENGTH) ? Optional.of(new Size(trimmed)) : Optional.empty();
    }
}
