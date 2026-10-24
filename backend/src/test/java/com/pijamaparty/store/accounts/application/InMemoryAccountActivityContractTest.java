package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.activity.api.ActivityEntry;
import java.util.List;

class InMemoryAccountActivityContractTest extends AccountActivityContractTest<InMemoryAccountActivity> {

    @Override
    protected InMemoryAccountActivity createSubject() {
        return new InMemoryAccountActivity();
    }

    @Override
    protected List<ActivityEntry> recorded() {
        return subject.entries();
    }
}
