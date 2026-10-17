package com.pijamaparty.store.promotions.domain;

import java.util.Objects;

/** A coupon with its uses so far: the paid orders that used it (UC-15). */
public record CountedCoupon(Coupon coupon, long uses) {

    public CountedCoupon {
        Objects.requireNonNull(coupon, "coupon");
        if (uses < 0) {
            throw new IllegalArgumentException("uses cannot be negative");
        }
    }
}
