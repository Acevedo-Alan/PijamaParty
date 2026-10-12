package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.promotions.domain.CouponId;

class InMemoryCouponStoreContractTest extends CouponStoreContractTest<InMemoryCouponStore> {

    @Override
    protected InMemoryCouponStore createSubject() {
        return new InMemoryCouponStore();
    }

    @Override
    protected void givenRedemptions(CouponId id, int orders) {
        subject.redeem(id, orders);
    }
}
