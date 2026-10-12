package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.platform.domain.Money;

class InMemoryCouponsContractTest extends CouponsContractTest<InMemoryCoupons> {

    @Override
    protected InMemoryCoupons createSubject() {
        return new InMemoryCoupons();
    }

    @Override
    protected CouponId givenPercentageCoupon(String code, int percent, Money minimum) {
        return subject.percentage(code, percent, minimum);
    }

    @Override
    protected CouponId givenExpiredCoupon(String code) {
        CouponId id = subject.percentage(code, 10);
        subject.reject(id, CouponCheck.Status.EXPIRED);
        return id;
    }
}
