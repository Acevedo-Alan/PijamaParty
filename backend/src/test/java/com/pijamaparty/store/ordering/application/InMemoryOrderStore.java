package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Fake of {@link OverdueOrders}, {@link OrderTransitions}, {@link SaveOrders}, {@link OrderNumbers} and
 * {@link OrdersToPay} over the same orders. Locks are not modeled.
 */
public class InMemoryOrderStore implements OverdueOrders, OrderTransitions, SaveOrders, OrderNumbers, OrdersToPay {

    /** Oldest expiry first; the id breaks ties as PostgreSQL orders uuids (as the canonical text does). */
    private static final Comparator<Map.Entry<OrderId, Row>> OLDEST_EXPIRY_FIRST =
        Comparator.comparing((Map.Entry<OrderId, Row> entry) -> entry.getValue().reservationExpiresAt())
            .thenComparing(entry -> entry.getKey().value().toString());

    private final Map<OrderId, Row> orders = new HashMap<>();
    private final Map<OrderId, NewOrder> placed = new HashMap<>();
    private final Set<Long> numbers = new HashSet<>();
    private final Set<String> trackingTokenHashes = new HashSet<>();
    private long lastNumber = 1000;

    /** Test arrangement: an order in any status, without its details. */
    public synchronized void add(OrderId order, OrderStatus status, Instant reservationExpiresAt) {
        orders.put(order, new Row(status, reservationExpiresAt));
    }

    /** Test inspection: empty when there is no such order. */
    public synchronized Optional<OrderStatus> statusOf(OrderId order) {
        return Optional.ofNullable(orders.get(order)).map(Row::status);
    }

    /** Test inspection: the order as the checkout placed it; empty when it placed no such order. */
    public synchronized Optional<NewOrder> placed(OrderId order) {
        return Optional.ofNullable(placed.get(order));
    }

    /** Test inspection: how many orders the checkout placed. */
    public synchronized int placedCount() {
        return placed.size();
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

    @Override
    public synchronized long next() {
        return ++lastNumber;
    }

    /** As the database: the id, the number and the tracking token hash are unique. */
    @Override
    public synchronized void insert(NewOrder order) {
        if (orders.containsKey(order.id()) || numbers.contains(order.number())
            || trackingTokenHashes.contains(order.trackingTokenHash())) {
            throw new IllegalStateException("duplicate order id, number or tracking token");
        }
        orders.put(order.id(), new Row(OrderStatus.AWAITING_PAYMENT, order.reservationExpiresAt()));
        placed.put(order.id(), order);
        numbers.add(order.number());
        trackingTokenHashes.add(order.trackingTokenHash());
    }

    /** Only the orders the checkout placed carry what paying needs. */
    @Override
    public synchronized Optional<OrderToPay> find(OrderId order) {
        return Optional.ofNullable(placed.get(order)).map(placedOrder -> new OrderToPay(order, placedOrder.number(),
            orders.get(order).status(), placedOrder.amounts().total(), placedOrder.paymentProvider(),
            placedOrder.buyer().name(), placedOrder.buyer().email(), placedOrder.reservationExpiresAt()));
    }

    private record Row(OrderStatus status, Instant reservationExpiresAt) {}
}
