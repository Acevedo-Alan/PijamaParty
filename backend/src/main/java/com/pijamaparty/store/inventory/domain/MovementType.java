package com.pijamaparty.store.inventory.domain;

/** Why the stock of a variant changed (UC-07). The order types arrive with checkout (E3, UC-16). */
public enum MovementType {
    /** The first movement of a variant, recorded by the administrator. */
    INITIAL_LOAD,
    /** Every later movement recorded by the administrator. */
    ADJUSTMENT,
    /** An order awaiting payment holds units: negative. */
    RESERVATION,
    /** An approved payment takes the reserved units for good: negative. */
    DEDUCTION,
    /** An expired or rejected order gives its reserved units back: positive. */
    RELEASE;

    /** Recorded by the administrator, so it has a user and an adjustment key. */
    public boolean isAdministrative() {
        return this == INITIAL_LOAD || this == ADJUSTMENT;
    }

    /** Recorded for an order, so it has one (UC-16). */
    public boolean isOrderMovement() {
        return !isAdministrative();
    }
}
