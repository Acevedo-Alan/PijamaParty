package com.pijamaparty.store.catalog.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What a visitor searches for (UC-26): trimmed, without control or invisible characters, 2 to 100 characters. The
 * text is always bound as a parameter; {@link #words()} are the only part that may shape a query.
 */
public record SearchText(String value) {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 100;

    /** Letters, with their combining accents, and digits. */
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}\\p{N}]+");

    public SearchText {
        Objects.requireNonNull(value, "value");
        int length = value.codePointCount(0, value.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH || !value.equals(value.strip())
            || !value.codePoints().allMatch(PlainText::isVisibleOrSpace)) {
            throw new IllegalArgumentException("not a search text");
        }
    }

    /**
     * The search of a request: control and invisible characters become spaces, the text is trimmed and cut to its
     * first {@link #MAX_LENGTH} characters. Empty when fewer than {@link #MIN_LENGTH} characters remain.
     */
    public static Optional<SearchText> parse(String raw) {
        int[] visible = raw.codePoints().map(c -> PlainText.isVisibleOrSpace(c) ? c : ' ').toArray();
        String text = new String(visible, 0, visible.length).strip();
        if (text.codePointCount(0, text.length()) > MAX_LENGTH) {
            text = text.substring(0, text.offsetByCodePoints(0, MAX_LENGTH)).strip();
        }
        return text.codePointCount(0, text.length()) < MIN_LENGTH ? Optional.empty() : Optional.of(new SearchText(text));
    }

    /** The runs of letters and digits, in order; quotes, symbols and operators are left out. */
    public List<String> words() {
        return WORD.matcher(value).results().map(match -> match.group()).toList();
    }
}
