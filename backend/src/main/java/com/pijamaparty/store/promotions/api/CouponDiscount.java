package com.pijamaparty.store.promotions.api;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-10: a coupon evaluated for a subtotal.
 *
 * @param code the stored code, in upper case
 * @param discount what it takes off the subtotal; zero unless {@link CouponStatus#APPLICABLE}
 * @param missing what the subtotal lacks to reach the minimum purchase; zero unless
 *                {@link CouponStatus#BELOW_MINIMUM}
 */
public record CouponDiscount(UUID couponId, String code, CouponStatus status, Money discount, Money missing) {

    public CouponDiscount {
        Objects.requireNonNull(couponId, "couponId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(missing, "missing");
        if (status != CouponStatus.APPLICABLE && !discount.isZero()) {
            throw new IllegalArgumentException("only an applicable coupon discounts");
        }
        if (status == CouponStatus.BELOW_MINIMUM == missing.isZero()) {
            throw new IllegalArgumentException("a missing amount, and only below the minimum");
        }
    }
}
