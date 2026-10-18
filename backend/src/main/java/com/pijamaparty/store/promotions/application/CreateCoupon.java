package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Set;

/** UC-11, UC-23: the administrator creates a code coupon or an automatic one; it is active. */
public interface CreateCoupon {

    Result execute(Command command);

    /** @param automatic whether it applies on its own (UC-23); it cannot change later */
    record Command(Actor actor, CouponInput coupon, boolean automatic) {

        /** A code coupon. */
        public Command(Actor actor, CouponInput coupon) {
            this(actor, coupon, false);
        }
    }

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
