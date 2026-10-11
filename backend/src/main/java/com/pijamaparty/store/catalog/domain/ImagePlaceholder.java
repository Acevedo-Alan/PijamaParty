package com.pijamaparty.store.catalog.domain;

import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The tiny blurred WebP of a photo as a {@code data:} URI, shown while the photo loads (UC-06). Only base64 after the
 * prefix, so it cannot break out of an HTML attribute or a CSS {@code url()} (V3).
 */
public record ImagePlaceholder(String value) {

    public static final int MAX_LENGTH = 4000;
    private static final String PREFIX = "data:image/webp;base64,";
    private static final Pattern FORMAT = Pattern.compile("data:image/webp;base64,[A-Za-z0-9+/]+={0,2}");

    public ImagePlaceholder {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("not a base64 WebP data URI of up to " + MAX_LENGTH + " characters");
        }
    }

    public static ImagePlaceholder ofWebp(byte[] webp) {
        return new ImagePlaceholder(PREFIX + Base64.getEncoder().encodeToString(webp));
    }
}
