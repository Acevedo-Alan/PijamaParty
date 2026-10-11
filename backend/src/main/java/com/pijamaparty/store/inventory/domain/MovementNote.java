package com.pijamaparty.store.inventory.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * The administrator's note on a stock movement, shown as its reason (UC-07): trimmed, 1 to 200 characters, one line,
 * without control or invisible characters. Free text may hold personal data, so it never prints its value.
 */
public record MovementNote(String value) {

    /** 00-dominio-y-reglas.md, maximum lengths. */
    public static final int MAX_LENGTH = 200;

    public MovementNote {
        Objects.requireNonNull(value, "value");
        if (!isValid(value)) {
            throw new IllegalArgumentException("not a valid note");
        }
    }

    /** Trims what the administrator typed; empty when it cannot be a note (including an empty text). */
    public static Optional<MovementNote> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        return isValid(trimmed) ? Optional.of(new MovementNote(trimmed)) : Optional.empty();
    }

    /** Lengths count characters (code points), as PostgreSQL's {@code char_length} does. */
    private static boolean isValid(String value) {
        return !value.isEmpty() && value.equals(value.strip())
            && value.codePointCount(0, value.length()) <= MAX_LENGTH
            && value.codePoints().allMatch(MovementNote::isVisibleOrSpace);
    }

    /** No control characters (line breaks included), invisible format characters or lone surrogates. */
    private static boolean isVisibleOrSpace(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.LINE_SEPARATOR,
                 Character.PARAGRAPH_SEPARATOR, Character.UNASSIGNED, Character.PRIVATE_USE -> false;
            default -> true;
        };
    }

    @Override
    public String toString() {
        return "MovementNote[***]";
    }
}
