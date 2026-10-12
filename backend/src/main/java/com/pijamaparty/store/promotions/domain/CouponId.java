package com.pijamaparty.store.promotions.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of a {@link Coupon}. */
public record CouponId(UUID value) {

    public CouponId {
        Objects.requireNonNull(value, "value");
    }

    public static CouponId of(UUID value) {
        return new CouponId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
