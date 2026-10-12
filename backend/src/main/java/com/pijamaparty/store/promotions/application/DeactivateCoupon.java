package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.promotions.domain.CouponId;

/**
 * UC-11: the administrator deactivates a coupon, also one with uses; a cart that holds it gets no discount (UC-10).
 * An inactive one stays as it is.
 */
public interface DeactivateCoupon {

    Result execute(Command command);

    record Command(Actor actor, CouponId couponId) {}

    sealed interface Result {

        record Deactivated(CouponView coupon) implements Result {}

        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
