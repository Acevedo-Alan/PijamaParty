package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.accounts.api.CustomerCounts.Counts;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake of {@link CustomerFigures}: the customers the test registers; administrators are never added. */
public class InMemoryCustomerFigures implements CustomerFigures {

    private final List<Instant> registered = new CopyOnWriteArrayList<>();

    public void registered(Instant at) {
        registered.add(at);
    }

    @Override
    public Counts count(Instant since) {
        return new Counts(registered.size(), registered.stream().filter(at -> !at.isBefore(since)).count());
    }
}
