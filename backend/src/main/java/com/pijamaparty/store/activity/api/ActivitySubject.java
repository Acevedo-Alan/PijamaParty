package com.pijamaparty.store.activity.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-37: what a change was about.
 *
 * @param id empty for a subject without an id of its own (the shipping table, the store settings)
 * @param label how the screen names it; never personal data: a product or category name, a SKU, a coupon code, an
 *              order number such as {@code #1001}, a withdrawal code, a province. Empty when the id says enough.
 */
public record ActivitySubject(SubjectType type, Optional<UUID> id, Optional<String> label) {

    public ActivitySubject {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
    }

    public static ActivitySubject of(SubjectType type, UUID id, String label) {
        return new ActivitySubject(type, Optional.of(id), Optional.of(label));
    }

    public static ActivitySubject of(SubjectType type, UUID id) {
        return new ActivitySubject(type, Optional.of(id), Optional.empty());
    }

    /** A subject without an id of its own, such as the shipping table or the store settings. */
    public static ActivitySubject of(SubjectType type) {
        return new ActivitySubject(type, Optional.empty(), Optional.empty());
    }
}
