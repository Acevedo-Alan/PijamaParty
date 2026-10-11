package com.pijamaparty.store.catalog.domain;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Server-generated base name of a photo's files (RNF-29.4): lower-case letters, digits and hyphens, never the name of
 * the uploaded file. Each rendition is stored as {@code <key>-<width>.webp}.
 */
public record StorageKey(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z0-9][a-z0-9-]{0,99}");

    public StorageKey {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("not a valid storage key");
        }
    }

    public static StorageKey of(UUID random) {
        return new StorageKey(random.toString());
    }

    /** The file of the rendition {@code width} pixels wide. */
    public String fileName(int width) {
        return value + "-" + width + ".webp";
    }
}
