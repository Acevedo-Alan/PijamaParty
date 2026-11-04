package com.pijamaparty.store.ordering.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake of {@link OrderCountsByCustomer}: the orders the test gives, each linked to a customer or a guest's. */
public class InMemoryOrderCountsByCustomer implements OrderCountsByCustomer {

    private final List<Optional<UUID>> orders = new CopyOnWriteArrayList<>();

    public void order(Optional<UUID> customer) {
        orders.add(customer);
    }

    @Override
    public Map<UUID, Long> countsOf(Collection<UUID> customerIds) {
        var counts = new HashMap<UUID, Long>();
        orders.stream().flatMap(Optional::stream).filter(customerIds::contains)
            .forEach(id -> counts.merge(id, 1L, Long::sum));
        return Map.copyOf(counts);
    }
}
