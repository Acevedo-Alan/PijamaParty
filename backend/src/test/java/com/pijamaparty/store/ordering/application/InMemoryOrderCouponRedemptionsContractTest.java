package com.pijamaparty.store.ordering.application;

import java.util.UUID;

class InMemoryOrderCouponRedemptionsContractTest
    extends OrderCouponRedemptionsContractTest<InMemoryOrderCouponRedemptions> {

    @Override
    protected InMemoryOrderCouponRedemptions createSubject() {
        return new InMemoryOrderCouponRedemptions();
    }

    @Override
    protected UUID givenCoupon() {
        UUID coupon = UUID.randomUUID();
        subject.coupon(coupon);
        return coupon;
    }

    @Override
    protected long usesOf(UUID couponId) {
        return subject.usesOf(couponId);
    }
}
