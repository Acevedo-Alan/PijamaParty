package com.pijamaparty.store.platform.persistence;

import java.util.Objects;

/**
 * The only way to put user text into a {@code LIKE} (RNF-28): the wildcards {@code %} and {@code _} and the escape
 * character itself are escaped, so a search for "50%" does not match everything that starts with "50". Bind the result
 * as a parameter and declare the escape character:
 *
 * <pre>{@code
 * where p.name like :q escape '\'           (JPQL / SQL)
 * cb.like(root.get(NAME), LikePattern.contains(text), LikePattern.ESCAPE)   (Criteria)
 * }</pre>
 */
public final class LikePattern {

    public static final char ESCAPE = '\\';

    private LikePattern() {
    }

    /** Matches values that contain {@code text}. */
    public static String contains(String text) {
        return "%" + escape(text) + "%";
    }

    /** Matches values that start with {@code text}. */
    public static String startsWith(String text) {
        return escape(text) + "%";
    }

    static String escape(String text) {
        Objects.requireNonNull(text, "text");
        var escaped = new StringBuilder(text.length() + 8);
        for (char c : text.toCharArray()) {
            if (c == '%' || c == '_' || c == ESCAPE) {
                escaped.append(ESCAPE);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
