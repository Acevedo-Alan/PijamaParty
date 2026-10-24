package com.pijamaparty.store.activity.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * UC-37: the size of an entry. A field key is a stable identifier; a changed value or a subject label is display text
 * of at most 200 characters, cut with an ellipsis when longer (a product description can reach 5,000), so recording a
 * change never refuses the change itself; an entry has at most 20 changed fields.
 */
public final class ActivityLimits {

    public static final int MAX_TEXT = 200;
    public static final int MAX_CHANGES = 20;

    private static final Pattern FIELD = Pattern.compile("[a-z][A-Za-z0-9]{0,39}");
    private static final String ELLIPSIS = "…";

    private ActivityLimits() {
    }

    /** A field key such as {@code price} or {@code trackingNumber}; anything else is a programming error. */
    public static String field(String key) {
        Objects.requireNonNull(key, "key");
        if (!FIELD.matcher(key).matches()) {
            throw new IllegalArgumentException("not a field key: " + key);
        }
        return key;
    }

    /** {@code text} cut to {@value #MAX_TEXT} characters (code points), the last one an ellipsis when it was longer. */
    public static String text(String text) {
        Objects.requireNonNull(text, "text");
        if (text.codePointCount(0, text.length()) <= MAX_TEXT) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, MAX_TEXT - 1)) + ELLIPSIS;
    }

    /** Refuses more than {@value #MAX_CHANGES} changed fields: a use case records what it changed, not a dump. */
    public static int changes(int count) {
        if (count < 0 || count > MAX_CHANGES) {
            throw new IllegalArgumentException("an entry has 0 to " + MAX_CHANGES + " changed fields, not " + count);
        }
        return count;
    }
}
