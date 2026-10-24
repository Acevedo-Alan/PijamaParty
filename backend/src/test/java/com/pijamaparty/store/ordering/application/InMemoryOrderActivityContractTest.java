package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.activity.api.ActivityEntry;
import java.util.List;

class InMemoryOrderActivityContractTest extends OrderActivityContractTest<InMemoryOrderActivity> {

    @Override
    protected InMemoryOrderActivity createSubject() {
        return new InMemoryOrderActivity();
    }

    @Override
    protected List<ActivityEntry> recorded() {
        return subject.entries();
    }
}
