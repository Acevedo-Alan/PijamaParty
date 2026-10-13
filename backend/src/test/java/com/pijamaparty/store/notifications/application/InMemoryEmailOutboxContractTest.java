package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.application.InMemoryEmailOutbox.Stored;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.util.Optional;
import java.util.UUID;

class InMemoryEmailOutboxContractTest extends EmailOutboxContractTest<InMemoryEmailOutbox> {

    @Override
    protected InMemoryEmailOutbox createSubject() {
        return new InMemoryEmailOutbox();
    }

    @Override
    protected Optional<Stored> stored(EmailType type, UUID reference) {
        return subject.find(type, reference);
    }
}
