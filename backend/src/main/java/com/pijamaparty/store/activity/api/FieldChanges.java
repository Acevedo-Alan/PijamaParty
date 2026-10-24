package com.pijamaparty.store.activity.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** UC-37: the fields a change touched, each added only when its value before and after differ. */
public final class FieldChanges {

    private final List<FieldChange> changes = new ArrayList<>();

    /** @param before null or empty when the field had no value; {@code after} likewise */
    public FieldChanges add(String field, String before, String after) {
        String from = valueOf(before);
        String to = valueOf(after);
        if (!Objects.equals(from, to)) {
            changes.add(FieldChange.of(field, from, to));
        }
        return this;
    }

    /**
     * Adds the field when {@code changed}, even if both values read the same, such as a count of products when one
     * product was swapped for another.
     */
    public FieldChanges addWhen(boolean changed, String field, String before, String after) {
        if (changed) {
            changes.add(FieldChange.of(field, valueOf(before), valueOf(after)));
        }
        return this;
    }

    /** An empty text, such as a banner without body, is no value. */
    private static String valueOf(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public List<FieldChange> list() {
        return List.copyOf(changes);
    }
}
