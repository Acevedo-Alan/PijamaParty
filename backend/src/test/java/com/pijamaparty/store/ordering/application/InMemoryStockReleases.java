package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Fake of {@link StockReleases}: the stock of each variant and what each order reserved of it. */
public class InMemoryStockReleases implements StockReleases {

    private final Map<UUID, Integer> onHand = new HashMap<>();
    private final Map<OrderId, Map<UUID, Integer>> reservations = new HashMap<>();
    private final Set<OrderId> released = new HashSet<>();

    /** Test arrangement: a new variant with {@code onHand} units, of which the order reserved {@code reserved}. */
    public synchronized void reserved(OrderId order, UUID variantId, int onHand, int reserved) {
        this.onHand.put(variantId, onHand);
        reservations.computeIfAbsent(order, key -> new HashMap<>()).put(variantId, reserved);
    }

    /** Test inspection: on hand − reserved. */
    public synchronized int availableOf(UUID variantId) {
        int reserved = reservations.entrySet().stream()
            .filter(entry -> !released.contains(entry.getKey()))
            .mapToInt(entry -> entry.getValue().getOrDefault(variantId, 0))
            .sum();
        return onHand.getOrDefault(variantId, 0) - reserved;
    }

    /** Test inspection: whether the order's stock was released. */
    public synchronized boolean wasReleased(OrderId order) {
        return released.contains(order);
    }

    @Override
    public synchronized void release(OrderId order) {
        if (reservations.containsKey(order)) {
            released.add(order);
        }
    }
}
