package com.pijamaparty.store.backoffice.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake of {@link CustomerOrderTotals}: the orders the test gives, each linked to a customer or a guest's. */
public class InMemoryCustomerOrderTotals implements CustomerOrderTotals {

    private final List<Optional<UUID>> orders = new CopyOnWriteArrayList<>();

    public void order(Optional<UUID> customer) {
        orders.add(customer);
    }

    @Override
    public Map<UUID, Long> countsOf(Collection<UUID> customerIds) {
        var counts = new HashMap<UUID, Long>();
        customerIds.forEach(id -> counts.put(id, orders.stream().filter(Optional.of(id)::equals).count()));
        return Map.copyOf(counts);
    }
}
