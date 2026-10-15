package com.pijamaparty.store.ordering.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * UC-29 (T-E3-25): the procedure code of a withdrawal request, {@code ARR-} and {@value #LENGTH} characters of
 * {@link #ALPHABET}, which leaves out the characters read alike ({@code 0}, {@code 1}, {@code I}, {@code L}, {@code O}).
 * Not personal data.
 */
public record WithdrawalCode(String value) {

    public static final String PREFIX = "ARR-";
    public static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    public static final int LENGTH = 8;

    private static final Pattern FORMAT = Pattern.compile(Pattern.quote(PREFIX) + "[" + ALPHABET + "]{" + LENGTH + "}");

    public WithdrawalCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("not a withdrawal code: " + value);
        }
    }
}
