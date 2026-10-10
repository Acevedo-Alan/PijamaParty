package com.pijamaparty.store.catalog.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A color of a {@link Product}, free text shown in the order the administrator gave (UC-06): trimmed, 1 to 30
 * characters, one line (00-dominio-y-reglas.md, maximum lengths).
 */
public record Color(String value) {

    public static final int MAX_LENGTH = 30;

    public Color {
        Objects.requireNonNull(value, "value");
        if (!PlainText.isSingleLine(value, MAX_LENGTH)) {
            throw new IllegalArgumentException("not a valid color");
        }
    }

    /** Trims what the administrator typed; empty when it cannot be a color. */
    public static Optional<Color> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        return PlainText.isSingleLine(trimmed, MAX_LENGTH) ? Optional.of(new Color(trimmed)) : Optional.empty();
    }
}
