package com.pijamaparty.store.promotions.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;

/** UC-23: the one coupon that discounts a cart, and how much. */
public record AppliedDiscount(Coupon coupon, Money amount) {

    public AppliedDiscount {
        Objects.requireNonNull(coupon, "coupon");
        Objects.requireNonNull(amount, "amount");
    }
}
