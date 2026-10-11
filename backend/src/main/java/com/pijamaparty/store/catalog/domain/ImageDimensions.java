package com.pijamaparty.store.catalog.domain;

/** Width and height of an uploaded photo as its header states them, read before decoding it (RNF-29.4). */
public record ImageDimensions(int width, int height) {

    /** UC-06: the longest side the server decodes. */
    public static final int MAX_SIDE = 10_000;

    /** UC-06: the most pixels the server decodes, against decompression bombs. */
    public static final long MAX_PIXELS = 50_000_000L;

    public ImageDimensions {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("an image has at least one pixel");
        }
    }

    public boolean withinLimits() {
        return width <= MAX_SIDE && height <= MAX_SIDE && (long) width * height <= MAX_PIXELS;
    }
}
