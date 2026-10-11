package com.pijamaparty.store.catalog.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The URL name of a category or a product: lower-case ASCII letters and digits in groups joined by single hyphens,
 * the same format the database checks (V3).
 */
public record Slug(String value) {

    /** 00-dominio-y-reglas.md, maximum lengths: the longest slug (a product's); a category allows less. */
    public static final int MAX_LENGTH = 140;

    private static final Pattern FORMAT = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^a-z0-9]+");

    public Slug {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("not a slug");
        }
    }

    /** A slug as a visitor sent it (UC-03); empty when it is not one, so it cannot name anything. */
    public static Optional<Slug> parse(String text) {
        Objects.requireNonNull(text, "text");
        return text.length() <= MAX_LENGTH && FORMAT.matcher(text).matches()
            ? Optional.of(new Slug(text))
            : Optional.empty();
    }

    /**
     * UC-05: lower case, accents removed, every run of other characters turned into one hyphen and no hyphen at
     * either end ({@code "Pijamas de Niñas"} becomes {@code pijamas-de-ninas}). Empty when nothing is left or the
     * result is longer than {@code maxLength}.
     */
    public static Optional<Slug> fromText(String text, int maxLength) {
        Objects.requireNonNull(text, "text");
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD);
        String withoutAccents = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        String hyphenated = NOT_LETTER_OR_DIGIT.matcher(withoutAccents.toLowerCase(Locale.ROOT)).replaceAll("-");
        String slug = trimHyphens(hyphenated);
        return slug.isEmpty() || slug.length() > Math.min(maxLength, MAX_LENGTH)
            ? Optional.empty()
            : Optional.of(new Slug(slug));
    }

    private static String trimHyphens(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == '-') {
            start++;
        }
        while (end > start && text.charAt(end - 1) == '-') {
            end--;
        }
        return text.substring(start, end);
    }
}
