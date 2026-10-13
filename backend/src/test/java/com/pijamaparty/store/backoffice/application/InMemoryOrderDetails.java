package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.AdminOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Fake of {@link OrderDetails}: the orders as the ordering module would answer them. */
public class InMemoryOrderDetails implements OrderDetails {

    private final Map<UUID, AdminOrder> orders = new HashMap<>();

    /** Test arrangement: the ordering module knows this order. */
    public synchronized void add(AdminOrder order) {
        orders.put(order.orderId(), order);
    }

    @Override
    public synchronized Optional<AdminOrder> find(UUID orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }
}
