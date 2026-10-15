package com.pijamaparty.store.ordering.domain;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/** UC-29 (T-E3-25): an order number as a person types it, {@code 1001} or {@code #1001}, as the store shows it. */
public final class OrderNumberText {

    // At most 18 digits, so it always fits in a long.
    private static final Pattern NUMBER = Pattern.compile("#?([0-9]{1,18})");

    private OrderNumberText() {
    }

    /** The number, trimmed and without its {@code #}; empty when it is not one, or not positive. */
    public static OptionalLong parse(String typed) {
        var matcher = NUMBER.matcher(Objects.requireNonNullElse(typed, "").strip());
        if (!matcher.matches()) {
            return OptionalLong.empty();
        }
        long number = Long.parseLong(matcher.group(1));
        return number > 0 ? OptionalLong.of(number) : OptionalLong.empty();
    }
}
