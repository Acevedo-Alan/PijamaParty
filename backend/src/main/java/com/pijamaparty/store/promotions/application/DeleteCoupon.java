package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.promotions.domain.CouponId;

/** UC-11: the administrator deletes a coupon without uses; a used one can only be deactivated. */
public interface DeleteCoupon {

    Result execute(Command command);

    record Command(Actor actor, CouponId couponId) {}

    sealed interface Result {

        record Deleted() implements Result {}

        record NotFound() implements Result {}

        /** Not deleted: confirmed orders used it, so it can only be deactivated. */
        record HasUses() implements Result {}

        record Forbidden() implements Result {}
    }
}
