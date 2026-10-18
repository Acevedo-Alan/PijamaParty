package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.promotions.domain.CouponId;
import java.util.Set;

/**
 * UC-11: the administrator replaces every field of a coupon but its state and whether it is automatic (UC-23), also
 * when it has uses. Its uses stay, and a confirmed order keeps the discount it got (E3).
 */
public interface UpdateCoupon {

    Result execute(Command command);

    record Command(Actor actor, CouponId couponId, CouponInput coupon) {}

    sealed interface Result {

        record Updated(CouponView coupon) implements Result {}

        record NotFound() implements Result {}

        /** Another coupon already has this code, ignoring case. */
        record CodeAlreadyUsed(String code) implements Result {}

        record InvalidFields(Set<CouponInput.Field> fields) implements Result {

            public InvalidFields {
                fields = Set.copyOf(fields);
            }
        }

        record Forbidden() implements Result {}
    }
}
