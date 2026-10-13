package com.pijamaparty.store.ordering.application;

import java.util.List;

class InMemoryOrderEventsContractTest extends OrderEventsContractTest<InMemoryOrderEvents> {

    @Override
    protected InMemoryOrderEvents createSubject() {
        return new InMemoryOrderEvents();
    }

    @Override
    protected List<Object> published() {
        return subject.published();
    }
}
