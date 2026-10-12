package com.pijamaparty.store.cart.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed id of a promotions coupon, as the cart refers to it (no foreign key, ARCHITECTURE.md section 3, rule 6). */
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
