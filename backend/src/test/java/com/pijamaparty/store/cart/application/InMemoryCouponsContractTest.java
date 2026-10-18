package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.CouponId;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    @Override
    protected CouponId givenAutomaticCoupon(String code, int percent, Money minimum, Optional<UUID> category) {
        return category.isEmpty()
            ? subject.automatic(code, percent, minimum)
            : subject.automatic(code, percent, minimum, Set.of(category.get()));
    }
}
