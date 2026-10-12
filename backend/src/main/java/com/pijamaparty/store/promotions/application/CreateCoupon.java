package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Set;

/** UC-11: the administrator creates a coupon; it is active. */
public interface CreateCoupon {

    Result execute(Command command);

    record Command(Actor actor, CouponInput coupon) {}

    sealed interface Result {

        record Created(CouponView coupon) implements Result {}

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
