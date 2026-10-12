package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;

/** UC-11: the back office lists every coupon, active or not, newest first, with its number of uses. */
public interface ListCoupons {

    Result execute(Query query);

    record Query(Actor actor) {}

    sealed interface Result {

        record Listed(List<CouponView> coupons) implements Result {}

        record Forbidden() implements Result {}
    }
}
