package com.pijamaparty.store.ordering.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * UC-13, UC-17: the rule of the buyer's email, typed in the checkout and again when the buyer corrects it (T-E3-23).
 * Personal data (R-05): never logged.
 */
public final class BuyerEmail {

    // Printable ASCII without spaces, one '@' with text on both sides: lower case is then the same in Java and in
    // PostgreSQL, whose check compares with lower().
    private static final Pattern EMAIL = Pattern.compile("[\\x21-\\x3F\\x41-\\x7E]+@[\\x21-\\x3F\\x41-\\x7E]+");
    private static final int MAX_LENGTH = 254;

    private BuyerEmail() {
    }

    /** The email trimmed and in lower case, or empty when it breaks the rule. */
    public static Optional<String> normalize(String typed) {
        String normalized = Objects.requireNonNullElse(typed, "").strip().toLowerCase(Locale.ROOT);
        return normalized.length() <= MAX_LENGTH && EMAIL.matcher(normalized).matches()
            ? Optional.of(normalized)
            : Optional.empty();
    }
}
