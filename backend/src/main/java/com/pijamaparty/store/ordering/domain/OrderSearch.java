package com.pijamaparty.store.ordering.domain;

import java.util.Objects;

/**
 * UC-18 (T-E8-06): what the administrator types in the order search: an order number ({@code 1001} or {@code #1001},
 * the rule of UC-29), or else 2 to 100 characters of the buyer's name or email. Personal data: never logged.
 */
public sealed interface OrderSearch {

    int MIN_TEXT = 2;
    int MAX_TEXT = 100;

    /** That order only. */
    record ByNumber(long number) implements OrderSearch {

        public ByNumber {
            if (number <= 0) {
                throw new IllegalArgumentException("order numbers are positive");
            }
        }
    }

    /** The orders whose buyer's name or email contains the text, ignoring case and accents. */
    record ByBuyer(String text) implements OrderSearch {

        public ByBuyer {
            Objects.requireNonNull(text, "text");
            int length = text.codePointCount(0, text.length());
            if (length < MIN_TEXT || length > MAX_TEXT || !text.equals(text.strip())) {
                throw new IllegalArgumentException("a buyer search has 2 to 100 characters, trimmed");
            }
        }

        @Override
        public String toString() {
            return "ByBuyer[text=<hidden>]";
        }
    }

    /** What a search parses to: none when nothing was typed, invalid when it breaks the rule. */
    sealed interface Parsed {

        record None() implements Parsed {}

        record Valid(OrderSearch search) implements Parsed {}

        record Invalid() implements Parsed {}
    }

    /** {@code typed} trimmed: blank is no search, a number finds that order, other text 2 to 100 characters. */
    static Parsed parse(String typed) {
        String text = Objects.requireNonNullElse(typed, "").strip();
        if (text.isEmpty()) {
            return new Parsed.None();
        }
        var number = OrderNumberText.parse(text);
        if (number.isPresent()) {
            return new Parsed.Valid(new ByNumber(number.getAsLong()));
        }
        int length = text.codePointCount(0, text.length());
        return length < MIN_TEXT || length > MAX_TEXT ? new Parsed.Invalid() : new Parsed.Valid(new ByBuyer(text));
    }
}
