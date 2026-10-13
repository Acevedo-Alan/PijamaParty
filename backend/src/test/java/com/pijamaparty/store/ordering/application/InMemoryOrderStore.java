package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.PaidOrders.Address;
import com.pijamaparty.store.ordering.api.PaidOrders.Line;
import com.pijamaparty.store.ordering.api.PaidOrders.PaidOrder;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Fake of {@link OverdueOrders}, {@link OrderTransitions}, {@link SaveOrders}, {@link OrderNumbers},
 * {@link OrdersToPay}, {@link ConfirmableOrders}, {@link TrackedOrders}, {@link PaidOrderDetails} and
 * {@link SaveTrackingLinks} over the same orders. Locks are not modeled.
 */
public class InMemoryOrderStore implements OverdueOrders, OrderTransitions, SaveOrders, OrderNumbers, OrdersToPay,
    ConfirmableOrders, TrackedOrders, PaidOrderDetails, SaveTrackingLinks {

    /** Oldest expiry first; the id breaks ties as PostgreSQL orders uuids (as the canonical text does). */
    private static final Comparator<Map.Entry<OrderId, Row>> OLDEST_EXPIRY_FIRST =
        Comparator.comparing((Map.Entry<OrderId, Row> entry) -> entry.getValue().reservationExpiresAt())
            .thenComparing(entry -> entry.getKey().value().toString());

    private final Map<OrderId, Row> orders = new HashMap<>();
    private final Map<OrderId, NewOrder> placed = new HashMap<>();
    private final Set<Long> numbers = new HashSet<>();
    private final Set<String> trackingTokenHashes = new HashSet<>();
    private final Map<OrderId, String> shipmentTrackingNumbers = new HashMap<>();
    private final Map<String, OrderId> trackingLinks = new HashMap<>();
    private long lastNumber = 1000;

    /** Test arrangement: an order in any status, without its details nor a coupon; answers its number. */
    public synchronized long add(OrderId order, OrderStatus status, Instant reservationExpiresAt) {
        return add(order, status, reservationExpiresAt, Optional.empty());
    }

    /** Test arrangement: an order in any status that applied the coupon, if any, without its details. */
    public synchronized long add(OrderId order, OrderStatus status, Instant reservationExpiresAt,
                                 Optional<UUID> couponId) {
        long number = next();
        numbers.add(number);
        orders.put(order, new Row(number, status, reservationExpiresAt, couponId));
        return number;
    }

    /** Test arrangement: a placed order shipped with its shipment tracking number (UC-18), whatever its status. */
    public synchronized void ship(OrderId order, String shipmentTrackingNumber) {
        orders.put(order, orders.get(order).withStatus(OrderStatus.SHIPPED));
        shipmentTrackingNumbers.put(order, shipmentTrackingNumber);
    }

    /** Test inspection: empty when there is no such order. */
    public synchronized Optional<OrderStatus> statusOf(OrderId order) {
        return Optional.ofNullable(orders.get(order)).map(Row::status);
    }

    /** Test inspection: the order as the checkout placed it; empty when it placed no such order. */
    public synchronized Optional<NewOrder> placed(OrderId order) {
        return Optional.ofNullable(placed.get(order));
    }

    /** Test inspection: the order an extra tracking link (T-E3-09) opens; empty when there is no such link. */
    public synchronized Optional<OrderId> linkedOrder(String tokenHash) {
        return Optional.ofNullable(trackingLinks.get(tokenHash));
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
        orders.put(order, row.withStatus(to));
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
        orders.put(order.id(), new Row(order.number(), OrderStatus.AWAITING_PAYMENT, order.reservationExpiresAt(),
            order.coupon().map(AppliedCoupon::couponId)));
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

    /** Only the orders the checkout placed carry a tracking token. */
    @Override
    public synchronized Optional<TrackedOrder> find(TrackingToken token) {
        return placed.values().stream()
            .filter(order -> order.trackingTokenHash().equals(token.hash())
                || order.id().equals(trackingLinks.get(token.hash())))
            .findFirst()
            .map(order -> new TrackedOrder(order.id(), order.number(), orders.get(order.id()).status(),
                order.createdAt(), order.buyer().name(), order.address().city(), order.province(), order.lines(),
                order.amounts(), order.coupon().map(AppliedCoupon::code), order.reservationExpiresAt(),
                Optional.ofNullable(shipmentTrackingNumbers.get(order.id()))));
    }

    /** Only the orders the checkout placed carry what the emails tell. */
    @Override
    public synchronized Optional<PaidOrder> detailsOf(OrderId order) {
        return Optional.ofNullable(placed.get(order)).map(placedOrder -> new PaidOrder(order.value(),
            placedOrder.number(), placedOrder.buyer().name(), placedOrder.buyer().email(),
            placedOrder.lines().stream().map(line -> new Line(line.productName(), line.size(), line.color(),
                line.quantity(), line.unitPrice(), line.amount())).toList(),
            placedOrder.amounts().subtotal(), placedOrder.amounts().discount(),
            placedOrder.coupon().map(AppliedCoupon::code), placedOrder.amounts().shipping(),
            placedOrder.amounts().total(),
            new Address(placedOrder.address().street(), placedOrder.address().number(),
                placedOrder.address().floor(), placedOrder.address().apartment(), placedOrder.address().city(),
                placedOrder.province().name(), placedOrder.address().postalCode())));
    }

    /** As the database: the hash is unique, and only an existing order gets one. */
    @Override
    public synchronized boolean add(OrderId order, String tokenHash, Instant at) {
        if (!orders.containsKey(order)) {
            return false;
        }
        if (trackingLinks.containsKey(tokenHash)) {
            throw new IllegalStateException("duplicate tracking token");
        }
        trackingLinks.put(tokenHash, order);
        return true;
    }

    @Override
    public synchronized Optional<ConfirmableOrder> lock(OrderId order) {
        return Optional.ofNullable(orders.get(order))
            .map(row -> new ConfirmableOrder(order, row.number(), row.status(), row.couponId()));
    }

    private record Row(long number, OrderStatus status, Instant reservationExpiresAt, Optional<UUID> couponId) {

        Row withStatus(OrderStatus newStatus) {
            return new Row(number, newStatus, reservationExpiresAt, couponId);
        }
    }
}
