package com.pijamaparty.store.settings.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * UC-36: the store's WhatsApp number in the international format {@code wa.me} takes: 10 to 15 digits, such as
 * {@code 5493424000000}.
 */
public record WhatsAppNumber(String digits) {

    private static final Pattern DIGITS = Pattern.compile("[0-9]{10,15}");
    /** What people type between the digits, removed before checking: spaces, the leading + and dashes. */
    private static final Pattern SEPARATORS = Pattern.compile("[ +\\-]");

    public WhatsAppNumber {
        Objects.requireNonNull(digits, "digits");
        if (!DIGITS.matcher(digits).matches()) {
            throw new IllegalArgumentException("not a WhatsApp number");
        }
    }

    /** {@code +54 9 342 400-0000} as {@code 5493424000000}, or empty when what remains is not 10 to 15 digits. */
    public static Optional<WhatsAppNumber> parse(String typed) {
        String digits = SEPARATORS.matcher(Objects.requireNonNullElse(typed, "")).replaceAll("");
        return DIGITS.matcher(digits).matches() ? Optional.of(new WhatsAppNumber(digits)) : Optional.empty();
    }
}
