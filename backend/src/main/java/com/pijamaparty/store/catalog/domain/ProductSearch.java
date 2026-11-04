package com.pijamaparty.store.catalog.domain;

import java.util.Objects;

/**
 * UC-06 (T-E8-08): what the administrator types in the product search, 2 to 100 characters after trimming. It finds
 * the products whose name contains it, ignoring case and accents, and the one whose variant has it as SKU, ignoring
 * case.
 */
public record ProductSearch(String text) {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 100;

    public ProductSearch {
        Objects.requireNonNull(text, "text");
        int length = text.codePointCount(0, text.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH || !text.equals(text.strip())) {
            throw new IllegalArgumentException("a product search has 2 to 100 characters, trimmed");
        }
    }

    /** What a search parses to: none when nothing was typed, invalid when it breaks the rule. */
    public sealed interface Parsed {

        record None() implements Parsed {}

        record Valid(ProductSearch search) implements Parsed {}

        record Invalid() implements Parsed {}
    }

    /** {@code typed} trimmed: blank is no search, otherwise 2 to 100 characters. */
    public static Parsed parse(String typed) {
        String text = Objects.requireNonNullElse(typed, "").strip();
        if (text.isEmpty()) {
            return new Parsed.None();
        }
        int length = text.codePointCount(0, text.length());
        return length < MIN_LENGTH || length > MAX_LENGTH ? new Parsed.Invalid() : new Parsed.Valid(new ProductSearch(text));
    }
}
