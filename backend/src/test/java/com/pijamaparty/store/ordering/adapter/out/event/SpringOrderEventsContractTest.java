package com.pijamaparty.store.ordering.adapter.out.event;

import com.pijamaparty.store.ordering.application.OrderEventsContractTest;
import java.util.ArrayList;
import java.util.List;

/** {@link SpringOrderEvents} over a publisher that records the events, as Spring hands them to its listeners. */
class SpringOrderEventsContractTest extends OrderEventsContractTest<SpringOrderEvents> {

    private final List<Object> events = new ArrayList<>();

    @Override
    protected SpringOrderEvents createSubject() {
        return new SpringOrderEvents(events::add);
    }

    @Override
    protected List<Object> published() {
        return events;
    }
}
