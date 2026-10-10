package com.pijamaparty.store.accounts.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A user's email, normalized (trimmed, lower case) so sign-in and uniqueness ignore case. Personal data: never log
 * it (R-05); {@link #toString()} does not reveal it.
 */
public record Email(String value) {

    /** 00-dominio-y-reglas.md, maximum lengths. */
    public static final int MAX_LENGTH = 254;

    public Email {
        Objects.requireNonNull(value, "value");
        if (!isNormalizedAndWellFormed(value)) {
            throw new IllegalArgumentException("not a normalized email");
        }
    }

    /** Normalizes what a person typed; empty when it cannot be an email. */
    public static Optional<Email> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.strip().toLowerCase(Locale.ROOT);
        return isNormalizedAndWellFormed(normalized) ? Optional.of(new Email(normalized)) : Optional.empty();
    }

    private static boolean isNormalizedAndWellFormed(String value) {
        int at = value.indexOf('@');
        return value.length() <= MAX_LENGTH
            && value.equals(value.strip().toLowerCase(Locale.ROOT))
            && at > 0 && at == value.lastIndexOf('@') && at < value.length() - 1
            && value.chars().noneMatch(Character::isWhitespace);
    }

    @Override
    public String toString() {
        return "Email[***]";
    }
}
