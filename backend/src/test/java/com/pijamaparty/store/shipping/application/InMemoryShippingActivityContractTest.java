package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.activity.api.ActivityEntry;
import java.util.List;

class InMemoryShippingActivityContractTest extends ShippingActivityContractTest<InMemoryShippingActivity> {

    @Override
    protected InMemoryShippingActivity createSubject() {
        return new InMemoryShippingActivity();
    }

    @Override
    protected List<ActivityEntry> recorded() {
        return subject.entries();
    }
}
