package com.pijamaparty.store.inventory.domain;

import java.util.Objects;
import java.util.UUID;

/** The {@code Idempotency-Key} of a stock adjustment, unique per variant (UC-07): a retry with it changes nothing. */
public record AdjustmentKey(UUID value) {

    public AdjustmentKey {
        Objects.requireNonNull(value, "value");
    }

    public static AdjustmentKey of(UUID value) {
        return new AdjustmentKey(value);
    }
}
