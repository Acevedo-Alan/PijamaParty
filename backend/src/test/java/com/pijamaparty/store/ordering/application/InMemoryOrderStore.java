package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Fake of {@link OverdueOrders} and {@link OrderTransitions} over the same orders: only their status and reservation
 * expiry. Locks are not modeled.
 */
public class InMemoryOrderStore implements OverdueOrders, OrderTransitions {

    /** Oldest expiry first; the id breaks ties as PostgreSQL orders uuids (as the canonical text does). */
    private static final Comparator<Map.Entry<OrderId, Row>> OLDEST_EXPIRY_FIRST =
        Comparator.comparing((Map.Entry<OrderId, Row> entry) -> entry.getValue().reservationExpiresAt())
            .thenComparing(entry -> entry.getKey().value().toString());

    private final Map<OrderId, Row> orders = new HashMap<>();

    /** Test arrangement: an order as checkout stores it (T-E3-05), in any status. */
    public synchronized void add(OrderId order, OrderStatus status, Instant reservationExpiresAt) {
        orders.put(order, new Row(status, reservationExpiresAt));
    }

    /** Test inspection: empty when there is no such order. */
    public synchronized Optional<OrderStatus> statusOf(OrderId order) {
        return Optional.ofNullable(orders.get(order)).map(Row::status);
    }

    @Override
    public synchronized Optional<OrderId> lockNextDue(Instant now) {
        return orders.entrySet().stream()
            .filter(entry -> entry.getValue().status() == OrderStatus.AWAITING_PAYMENT)
            .filter(entry -> !entry.getValue().reservationExpiresAt().isAfter(now))
            .min(OLDEST_EXPIRY_FIRST)
            .map(Map.Entry::getKey);
    }

    @Override
    public synchronized boolean transition(OrderId order, OrderStatus from, OrderStatus to, Instant at) {
        if (!from.canBecome(to)) {
            throw new IllegalArgumentException("an order cannot go from " + from + " to " + to);
        }
        Row row = orders.get(order);
        if (row == null || row.status() != from) {
            return false;
        }
        orders.put(order, new Row(to, row.reservationExpiresAt()));
        return true;
    }

    private record Row(OrderStatus status, Instant reservationExpiresAt) {}
}
