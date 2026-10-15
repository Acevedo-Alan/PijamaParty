package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.AdminOrder;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Buyer;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.PaidOrders.Address;
import com.pijamaparty.store.ordering.api.PaidOrders.Line;
import com.pijamaparty.store.ordering.api.PaidOrders.PaidOrder;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.ShipmentTrackingNumber;
import com.pijamaparty.store.ordering.domain.StatusChange;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Fake of {@link OverdueOrders}, {@link OrderTransitions}, {@link SaveOrders}, {@link OrderNumbers},
 * {@link OrdersToPay}, {@link ConfirmableOrders}, {@link TrackedOrders}, {@link PaidOrderDetails},
 * {@link SaveTrackingLinks}, {@link OrderStatuses}, {@link OrderPages}, {@link AdminOrderViews}, {@link StatusChanges},
 * {@link ShipmentTrackingNumbers}, {@link LatePayments}, {@link CorrectableOrders} and {@link BuyerEmails} over the
 * same orders. Locks are not modeled.
 */
public class InMemoryOrderStore implements OverdueOrders, OrderTransitions, SaveOrders, OrderNumbers, OrdersToPay,
    ConfirmableOrders, TrackedOrders, PaidOrderDetails, SaveTrackingLinks, OrderStatuses, OrderPages, AdminOrderViews,
    StatusChanges, ShipmentTrackingNumbers, LatePayments, CorrectableOrders, BuyerEmails {

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
    private final Map<OrderId, Instant> updatedAt = new HashMap<>();
    private final Map<OrderId, Instant> deliveredAt = new HashMap<>();
    private final Set<OrderId> paidAfterClosing = new HashSet<>();
    private final Map<OrderId, String> correctedEmails = new HashMap<>();
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

    /** Test inspection: empty when the order has no shipment tracking number. */
    public synchronized Optional<String> shipmentTrackingNumberOf(OrderId order) {
        return Optional.ofNullable(shipmentTrackingNumbers.get(order));
    }

    /** Test inspection: empty when the order was not delivered. */
    public synchronized Optional<Instant> deliveredAtOf(OrderId order) {
        return Optional.ofNullable(deliveredAt.get(order));
    }

    /** Test inspection: whether the order was paid after it expired or was cancelled. */
    public synchronized boolean isPaidAfterClosing(OrderId order) {
        return paidAfterClosing.contains(order);
    }

    /** Test inspection: the buyer's email of an order the checkout placed, as last corrected. */
    public synchronized Optional<String> emailOf(OrderId order) {
        return Optional.ofNullable(placed.get(order)).map(this::buyerEmailOf);
    }

    /** Test inspection: when the order was last updated; empty when it never was. */
    public synchronized Optional<Instant> updatedAtOf(OrderId order) {
        return Optional.ofNullable(updatedAt.get(order));
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
        updatedAt.put(order, at);
        return true;
    }

    @Override
    public synchronized boolean change(OrderId order, StatusChange change, Instant at) {
        if (!transition(order, change.from(), change.to(), at)) {
            return false;
        }
        change.trackingNumber().ifPresent(number -> shipmentTrackingNumbers.put(order, number.value()));
        if (change.to() == OrderStatus.DELIVERED) {
            deliveredAt.put(order, at);
        }
        return true;
    }

    @Override
    public synchronized boolean correct(OrderId order, ShipmentTrackingNumber number, Instant at) {
        Row row = orders.get(order);
        if (row == null || row.status() != OrderStatus.SHIPPED) {
            return false;
        }
        shipmentTrackingNumbers.put(order, number.value());
        updatedAt.put(order, at);
        return true;
    }

    /** As the database: only a closed order is marked, once. */
    @Override
    public synchronized void paidAfterClosing(OrderId order, Instant at) {
        Row row = orders.get(order);
        if (row != null && (row.status() == OrderStatus.EXPIRED || row.status() == OrderStatus.CANCELLED)
            && paidAfterClosing.add(order)) {
            updatedAt.put(order, at);
        }
    }

    @Override
    public synchronized Optional<OrderStatus> lockStatus(OrderId order) {
        return statusOf(order);
    }

    /** Only the orders the checkout placed carry what the list shows. */
    @Override
    public synchronized List<OrderSummary> page(Optional<OrderStatus> status, int offset, int limit) {
        return placed.values().stream()
            .filter(order -> status.isEmpty() || orders.get(order.id()).status() == status.get())
            .sorted(Comparator.comparing(NewOrder::createdAt).thenComparing(NewOrder::number).reversed())
            .skip(offset)
            .limit(limit)
            .map(order -> new OrderSummary(order.id(), order.number(), order.createdAt(),
                orders.get(order.id()).status(), order.buyer().name(), order.address().city(), order.province(),
                order.amounts().total()))
            .toList();
    }

    /** Only the orders the checkout placed carry what the detail shows. */
    @Override
    public synchronized Optional<AdminOrder> adminViewOf(OrderId order) {
        return Optional.ofNullable(placed.get(order)).map(placedOrder -> new AdminOrder(order.value(),
            placedOrder.number(), Status.valueOf(orders.get(order).status().name()), List.of(),
            placedOrder.createdAt(), updatedAt.getOrDefault(order, placedOrder.createdAt()),
            Optional.ofNullable(deliveredAt.get(order)),
            new Buyer(placedOrder.buyer().name(), buyerEmailOf(placedOrder), placedOrder.buyer().phone(),
                placedOrder.buyer().nationalId()),
            new Address(placedOrder.address().street(), placedOrder.address().number(),
                placedOrder.address().floor(), placedOrder.address().apartment(), placedOrder.address().city(),
                placedOrder.province().name(), placedOrder.address().postalCode()),
            placedOrder.lines().stream().map(line -> new Line(line.productName(), line.size(), line.color(),
                line.quantity(), line.unitPrice(), line.amount())).toList(),
            placedOrder.amounts().subtotal(), placedOrder.amounts().discount(),
            placedOrder.coupon().map(AppliedCoupon::code), placedOrder.amounts().shipping(),
            placedOrder.amounts().total(), placedOrder.paymentProvider().name(),
            Optional.ofNullable(shipmentTrackingNumbers.get(order)), paidAfterClosing.contains(order)));
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
            placedOrder.buyer().name(), buyerEmailOf(placedOrder), placedOrder.reservationExpiresAt()));
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
            placedOrder.number(), placedOrder.buyer().name(), buyerEmailOf(placedOrder),
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

    /** Only the orders the checkout placed carry a tracking token. */
    @Override
    public synchronized Optional<CorrectableOrder> lock(TrackingToken token) {
        return find(token).map(order -> new CorrectableOrder(order.id(), order.number(), order.status()));
    }

    /** Only the orders the checkout placed carry a buyer. */
    @Override
    public synchronized boolean replace(OrderId order, String email, Instant at) {
        if (!placed.containsKey(order)) {
            return false;
        }
        correctedEmails.put(order, email);
        updatedAt.put(order, at);
        return true;
    }

    private String buyerEmailOf(NewOrder order) {
        return correctedEmails.getOrDefault(order.id(), order.buyer().email());
    }

    private record Row(long number, OrderStatus status, Instant reservationExpiresAt, Optional<UUID> couponId) {

        Row withStatus(OrderStatus newStatus) {
            return new Row(number, newStatus, reservationExpiresAt, couponId);
        }
    }
}
