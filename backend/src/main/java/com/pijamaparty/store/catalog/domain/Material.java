package com.pijamaparty.store.catalog.domain;

import java.util.Objects;
import java.util.Optional;

/** The material of a {@link Product}: trimmed, 1 to 80 characters, one line (00-dominio-y-reglas.md). */
public record Material(String value) {

    public static final int MAX_LENGTH = 80;

    public Material {
        Objects.requireNonNull(value, "value");
        if (!PlainText.isSingleLine(value, MAX_LENGTH)) {
            throw new IllegalArgumentException("not a valid material");
        }
    }

    /** Trims what the administrator typed; empty when it cannot be a material. */
    public static Optional<Material> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        return PlainText.isSingleLine(trimmed, MAX_LENGTH) ? Optional.of(new Material(trimmed)) : Optional.empty();
    }
}
