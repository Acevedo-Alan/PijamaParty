package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Fake of {@link StockReleases}, {@link StockDeductions} and {@link StockRestocks}: the stock of each variant and
 * what each order reserved of it. An order's reservation is either released or deducted, once; a deducted one may be
 * restocked, once.
 */
public class InMemoryStockReleases implements StockReleases, StockDeductions, StockRestocks {

    private final Map<UUID, Integer> onHand = new HashMap<>();
    private final Map<OrderId, Map<UUID, Integer>> reservations = new HashMap<>();
    private final Set<OrderId> released = new HashSet<>();
    private final Set<OrderId> deducted = new HashSet<>();
    private final Set<OrderId> restocked = new HashSet<>();

    /** Test arrangement: a new variant with {@code onHand} units, of which the order reserved {@code reserved}. */
    public synchronized void reserved(OrderId order, UUID variantId, int onHand, int reserved) {
        this.onHand.put(variantId, onHand);
        reservations.computeIfAbsent(order, key -> new HashMap<>()).put(variantId, reserved);
    }

    /** Test inspection: on hand − reserved. */
    public synchronized int availableOf(UUID variantId) {
        return onHandOf(variantId)
            - unitsOf(variantId, order -> !released.contains(order) && !deducted.contains(order));
    }

    /** Test inspection: the units on hand, after the deductions not restocked. */
    public synchronized int onHandOf(UUID variantId) {
        return onHand.getOrDefault(variantId, 0)
            - unitsOf(variantId, order -> deducted.contains(order) && !restocked.contains(order));
    }

    /** Test inspection: whether the order's stock was released. */
    public synchronized boolean wasReleased(OrderId order) {
        return released.contains(order);
    }

    /** Test inspection: whether the order's stock was deducted. */
    public synchronized boolean wasDeducted(OrderId order) {
        return deducted.contains(order);
    }

    @Override
    public synchronized void release(OrderId order) {
        if (reservations.containsKey(order) && !deducted.contains(order)) {
            released.add(order);
        }
    }

    @Override
    public synchronized void deduct(OrderId order) {
        if (reservations.containsKey(order) && !released.contains(order)) {
            deducted.add(order);
        }
    }

    /** Test inspection: whether the order's stock was put back. */
    public synchronized boolean wasRestocked(OrderId order) {
        return restocked.contains(order);
    }

    @Override
    public synchronized void restock(OrderId order) {
        if (deducted.contains(order)) {
            restocked.add(order);
        }
    }

    /** The units of the variant the orders matching {@code which} reserved. */
    private int unitsOf(UUID variantId, Predicate<OrderId> which) {
        return reservations.entrySet().stream()
            .filter(entry -> which.test(entry.getKey()))
            .mapToInt(entry -> entry.getValue().getOrDefault(variantId, 0))
            .sum();
    }
}
