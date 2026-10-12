package com.pijamaparty.store.promotions.api;

/** UC-10: whether a coupon applies to a subtotal now, or the first reason it does not. */
public enum CouponStatus {
    APPLICABLE,
    /** Deactivated by the administrator. */
    INACTIVE,
    /** Before its first valid day. */
    NOT_STARTED,
    /** After its last valid day. */
    EXPIRED,
    /** All its uses are taken. */
    USED_UP,
    /** The subtotal is below its minimum purchase. */
    BELOW_MINIMUM
}
