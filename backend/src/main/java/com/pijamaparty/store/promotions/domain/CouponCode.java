package com.pijamaparty.store.promotions.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The code a customer types (UC-10, UC-11): 3 to 20 letters, digits or hyphens, unique ignoring case, so it is kept
 * in upper case.
 */
public record CouponCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9-]{3,20}");

    public CouponCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("not a coupon code: 3 to 20 upper-case letters, digits or hyphens");
        }
    }

    /** What a customer typed, ignoring surrounding spaces and case; empty when it cannot be a code. */
    public static Optional<CouponCode> parse(String typed) {
        if (typed == null) {
            return Optional.empty();
        }
        String code = typed.strip().toUpperCase(Locale.ROOT);
        return FORMAT.matcher(code).matches() ? Optional.of(new CouponCode(code)) : Optional.empty();
    }

    @Override
    public String toString() {
        return value;
    }
}
