package com.pijamaparty.store.settings.domain;

import java.util.Optional;

/**
 * D-E8-03: a variant with this many available units or fewer is low on stock, one for the whole store. 0 means only
 * the sold-out variants.
 */
public record LowStockThreshold(int units) {

    public static final int MAX_UNITS = 1_000;
    /** The value of a new store. */
    public static final LowStockThreshold DEFAULT = new LowStockThreshold(3);

    public LowStockThreshold {
        if (units < 0 || units > MAX_UNITS) {
            throw new IllegalArgumentException("low-stock threshold out of range: " + units);
        }
    }

    /** The threshold as typed, or empty when it is outside 0 to 1,000. */
    public static Optional<LowStockThreshold> parse(long typed) {
        return typed < 0 || typed > MAX_UNITS ? Optional.empty() : Optional.of(new LowStockThreshold((int) typed));
    }
}
