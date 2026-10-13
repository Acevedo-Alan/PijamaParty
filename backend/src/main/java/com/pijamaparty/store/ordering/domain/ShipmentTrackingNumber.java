package com.pijamaparty.store.ordering.domain;

import java.util.Objects;
import java.util.Optional;

/** UC-18: the carrier's tracking number of a shipped order, 1 to 60 characters without surrounding spaces. */
public record ShipmentTrackingNumber(String value) {

    public static final int MAX_LENGTH = 60;

    public ShipmentTrackingNumber {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || value.length() > MAX_LENGTH || !value.equals(value.strip())) {
            throw new IllegalArgumentException("a tracking number has 1 to " + MAX_LENGTH + " characters, trimmed");
        }
    }

    /** The number as the administrator typed it, trimmed; empty when it is blank or too long. */
    public static Optional<ShipmentTrackingNumber> parse(String typed) {
        String trimmed = typed.strip();
        return trimmed.isEmpty() || trimmed.length() > MAX_LENGTH
            ? Optional.empty()
            : Optional.of(new ShipmentTrackingNumber(trimmed));
    }
}
