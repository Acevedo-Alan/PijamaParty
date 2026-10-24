package com.pijamaparty.store.activity.api;

import java.util.Objects;
import java.util.Optional;

/**
 * UC-37: one changed field, before and after, as display text (a price as {@code $22.000}, a status as its name), so
 * an old entry reads the same after any format change. Never personal data, free-text notes or secrets.
 *
 * @param field a stable key the frontend translates ({@code price}, {@code status}, {@code trackingNumber}...)
 * @param before empty when the field had no value
 * @param after empty when the field has no value any more
 */
public record FieldChange(String field, Optional<String> before, Optional<String> after) {

    public FieldChange {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }

    /** @param before null when the field had no value; {@code after} likewise */
    public static FieldChange of(String field, String before, String after) {
        return new FieldChange(field, Optional.ofNullable(before), Optional.ofNullable(after));
    }
}
