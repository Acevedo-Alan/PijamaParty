package com.pijamaparty.store.notifications.application;

class InMemoryEmailEventsContractTest extends EmailEventsContractTest<InMemoryEmailEvents> {

    @Override
    protected InMemoryEmailEvents createSubject() {
        return new InMemoryEmailEvents();
    }
}
