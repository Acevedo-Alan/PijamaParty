package com.pijamaparty.store.promotions.domain;

/** How a coupon discounts (UC-11). */
public enum CouponType {
    /** A whole percent of the subtotal, from 1 to 100. */
    PERCENTAGE,
    /** An amount in cents above 0, never more than the subtotal. */
    FIXED_AMOUNT
}
