package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.activity.api.ActivityEntry;
import java.util.List;

class InMemoryCouponActivityContractTest extends CouponActivityContractTest<InMemoryCouponActivity> {

    @Override
    protected InMemoryCouponActivity createSubject() {
        return new InMemoryCouponActivity();
    }

    @Override
    protected List<ActivityEntry> recorded() {
        return subject.entries();
    }
}
