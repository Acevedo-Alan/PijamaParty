package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.activity.api.ActivityEntry;
import java.util.List;

class InMemoryBackofficeActivityContractTest extends BackofficeActivityContractTest<InMemoryBackofficeActivity> {

    @Override
    protected InMemoryBackofficeActivity createSubject() {
        return new InMemoryBackofficeActivity();
    }

    @Override
    protected List<ActivityEntry> recorded() {
        return subject.entries();
    }
}
