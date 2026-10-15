package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.application.InMemoryEmailOutbox.Stored;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.util.List;
import java.util.UUID;

class InMemoryEmailOutboxContractTest extends EmailOutboxContractTest<InMemoryEmailOutbox> {

    @Override
    protected InMemoryEmailOutbox createSubject() {
        return new InMemoryEmailOutbox();
    }

    @Override
    protected List<Stored> storedVersions(EmailType type, UUID reference) {
        return subject.versions(type, reference);
    }
}
