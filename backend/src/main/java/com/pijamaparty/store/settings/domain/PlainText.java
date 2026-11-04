package com.pijamaparty.store.settings.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * UC-36: a short text the store shows as written (the header announcement, the opening hours, the address): trimmed,
 * on one line, without control characters, of at most its maximum length in characters.
 */
public record PlainText(String value) {

    public PlainText {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || !value.equals(value.strip()) || hasControlCharacters(value)) {
            throw new IllegalArgumentException("not a plain text");
        }
    }

    /**
     * The text trimmed, or empty when it breaks the rule. A blank text is not a {@link PlainText}: the caller treats
     * it as no value before parsing.
     */
    public static Optional<PlainText> parse(String typed, int maxLength) {
        String text = Objects.requireNonNullElse(typed, "").strip();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > maxLength || hasControlCharacters(text)) {
            return Optional.empty();
        }
        return Optional.of(new PlainText(text));
    }

    /** Line breaks, tabs and the other control characters, and the Unicode line and paragraph separators. */
    private static boolean hasControlCharacters(String text) {
        return text.codePoints().anyMatch(point -> Character.getType(point) == Character.CONTROL
            || point == ' ' || point == ' ');
    }
}
