package com.pijamaparty.store.inventory.domain;

/** Why the stock of a variant changed (UC-07). The reservation types arrive with checkout (E3). */
public enum MovementType {
    /** The first movement of a variant, recorded by the administrator. */
    INITIAL_LOAD,
    /** Every later movement recorded by the administrator. */
    ADJUSTMENT,
    RESERVATION,
    DEDUCTION,
    RELEASE;

    /** Recorded by the administrator, so it has a user and an adjustment key. */
    public boolean isAdministrative() {
        return this == INITIAL_LOAD || this == ADJUSTMENT;
    }
}
