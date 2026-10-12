package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;

/**
 * UC-10: a coupon evaluated for a cart's subtotal.
 *
 * @param code as stored, in upper case
 * @param discount zero unless {@link Status#APPLICABLE}
 * @param missing what the subtotal lacks to reach the minimum purchase; zero unless {@link Status#BELOW_MINIMUM}
 */
public record CouponCheck(CouponId id, String code, Status status, Money discount, Money missing) {

    public CouponCheck {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(missing, "missing");
        if (status != Status.APPLICABLE && !discount.isZero()) {
            throw new IllegalArgumentException("only an applicable coupon discounts");
        }
        if (status == Status.BELOW_MINIMUM == missing.isZero()) {
            throw new IllegalArgumentException("a missing amount, and only below the minimum");
        }
    }

    public boolean applies() {
        return status == Status.APPLICABLE;
    }

    /** Whether the coupon applies now, or the first reason it does not. */
    public enum Status {
        APPLICABLE,
        INACTIVE,
        NOT_STARTED,
        EXPIRED,
        USED_UP,
        BELOW_MINIMUM
    }
}
