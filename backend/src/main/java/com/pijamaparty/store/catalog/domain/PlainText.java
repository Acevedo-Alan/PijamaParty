package com.pijamaparty.store.catalog.domain;

/** Checks shared by the catalog's texts: trimmed, bounded and free of characters that hide or break text. */
final class PlainText {

    private PlainText() {
    }

    /** Trimmed, 1 to {@code maxLength} characters, one line. */
    static boolean isSingleLine(String value, int maxLength) {
        return !value.isEmpty() && isTrimmedWithin(value, maxLength)
            && value.codePoints().allMatch(PlainText::isVisibleOrSpace);
    }

    /** Trimmed, up to {@code maxLength} characters (possibly none), with line breaks allowed. */
    static boolean isMultiLine(String value, int maxLength) {
        return isTrimmedWithin(value, maxLength)
            && value.codePoints().allMatch(c -> c == '\n' || c == '\r' || isVisibleOrSpace(c));
    }

    /**
     * No control characters (NUL, tab, line breaks), invisible format characters (zero-width, bidi overrides) or lone
     * surrogates: they could make two texts look the same or the database reject the row.
     */
    static boolean isVisibleOrSpace(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.LINE_SEPARATOR,
                 Character.PARAGRAPH_SEPARATOR, Character.UNASSIGNED, Character.PRIVATE_USE -> false;
            default -> true;
        };
    }

    /** Lengths count characters (code points), as PostgreSQL's {@code char_length} does. */
    private static boolean isTrimmedWithin(String value, int maxLength) {
        return value.equals(value.strip()) && value.codePointCount(0, value.length()) <= maxLength;
    }
}
