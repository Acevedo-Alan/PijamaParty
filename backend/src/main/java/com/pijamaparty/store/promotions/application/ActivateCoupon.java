package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.promotions.domain.CouponId;

/** UC-11: the administrator activates a coupon, so carts can apply it again. An active one stays as it is. */
public interface ActivateCoupon {

    Result execute(Command command);

    record Command(Actor actor, CouponId couponId) {}

    sealed interface Result {

        record Activated(CouponView coupon) implements Result {}

        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
