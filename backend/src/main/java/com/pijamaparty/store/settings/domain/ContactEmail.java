package com.pijamaparty.store.settings.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * UC-36: the store's contact email, with the rule of UC-13's buyer email: at most 254 characters, printable ASCII
 * without spaces, one {@code @} with text on both sides, in lower case.
 */
public record ContactEmail(String value) {

    public static final int MAX_LENGTH = 254;

    // Printable ASCII without spaces or '@', as BuyerEmail: lower case is then the same in Java and in PostgreSQL.
    private static final Pattern EMAIL = Pattern.compile("[\\x21-\\x3F\\x41-\\x7E]+@[\\x21-\\x3F\\x41-\\x7E]+");

    public ContactEmail {
        Objects.requireNonNull(value, "value");
        if (!valid(value)) {
            throw new IllegalArgumentException("not a contact email");
        }
    }

    /** The email trimmed and in lower case, or empty when it breaks the rule. */
    public static Optional<ContactEmail> parse(String typed) {
        String normalized = Objects.requireNonNullElse(typed, "").strip().toLowerCase(Locale.ROOT);
        return valid(normalized) ? Optional.of(new ContactEmail(normalized)) : Optional.empty();
    }

    private static boolean valid(String email) {
        return email.length() <= MAX_LENGTH && EMAIL.matcher(email).matches()
            && email.equals(email.toLowerCase(Locale.ROOT));
    }
}
