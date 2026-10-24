package com.pijamaparty.store.activity.application;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

class InMemoryDeleteOldActivityContractTest extends DeleteOldActivityContractTest<InMemoryActivityStore> {

    @Override
    protected InMemoryActivityStore createSubject() {
        return new InMemoryActivityStore();
    }

    @Override
    protected void stored(RecordedActivity activity) {
        subject.save(activity);
    }

    @Override
    protected Set<UUID> remaining() {
        return subject.all().stream().map(RecordedActivity::id).collect(Collectors.toSet());
    }
}
