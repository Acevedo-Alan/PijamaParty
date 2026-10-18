package com.pijamaparty.store.reviews.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * UC-24: the name the product page shows as a review's author: only the first word of the account's name when the
 * review was last submitted. Personal data (R-05): {@link #toString()} never prints it.
 */
public record AuthorName(String value) {

    public static final int MAX_LENGTH = 100;

    public AuthorName {
        Objects.requireNonNull(value, "value");
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > MAX_LENGTH || value.codePoints().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("an author name is one word of 1 to " + MAX_LENGTH + " characters");
        }
    }

    /** The first word of {@code accountName}, cut to {@value #MAX_LENGTH} characters; empty when it has none. */
    public static Optional<AuthorName> fromAccountName(String accountName) {
        String first = accountName.strip().split("\\s+", 2)[0];
        if (first.isEmpty()) {
            return Optional.empty();
        }
        if (first.codePointCount(0, first.length()) > MAX_LENGTH) {
            first = first.substring(0, first.offsetByCodePoints(0, MAX_LENGTH));
        }
        return Optional.of(new AuthorName(first));
    }

    @Override
    public String toString() {
        return "AuthorName[***]";
    }
}
