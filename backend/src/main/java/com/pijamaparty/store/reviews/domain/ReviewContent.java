package com.pijamaparty.store.reviews.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * UC-24: what a customer says of a product, once {@link ReviewForm} validated it.
 *
 * @param rating a whole number from {@value #MIN_RATING} to {@value #MAX_RATING}
 * @param text trimmed, of 1 to {@value #MAX_TEXT} characters; empty for none
 */
public record ReviewContent(int rating, Optional<String> text) {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;
    public static final int MAX_TEXT = 1_000;

    public ReviewContent {
        Objects.requireNonNull(text, "text");
        if (!isValidRating(rating)) {
            throw new IllegalArgumentException("rating must be from " + MIN_RATING + " to " + MAX_RATING);
        }
        if (text.isPresent() && !isValidText(text.get())) {
            throw new IllegalArgumentException("text must be trimmed and of 1 to " + MAX_TEXT + " characters");
        }
    }

    static boolean isValidRating(int rating) {
        return rating >= MIN_RATING && rating <= MAX_RATING;
    }

    static boolean isValidText(String text) {
        int length = text.codePointCount(0, text.length());
        return text.equals(text.strip()) && length >= 1 && length <= MAX_TEXT;
    }

    @Override
    public String toString() {
        return "ReviewContent[rating=" + rating + ", text=***]";
    }
}
