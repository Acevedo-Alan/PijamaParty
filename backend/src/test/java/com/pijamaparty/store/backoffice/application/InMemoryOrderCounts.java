package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.OrderWorkload.RecentOrder;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Fake of {@link OrderCounts}: orders by status and withdrawal requests, as the test arranges them. */
public class InMemoryOrderCounts implements OrderCounts {

    private static final Set<Status> OPEN = EnumSet.of(Status.PAID, Status.PREPARING, Status.SHIPPED);

    private final List<RecentOrder> orders = new ArrayList<>();
    private long receivedWithdrawals;
    private long lastNumber = 1000;

    /** Test arrangement: an order of Ana Pérez in {@code status}, created at {@code createdAt}; answers its id. */
    public synchronized UUID order(Status status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        orders.add(new RecentOrder(id, ++lastNumber, createdAt, "Ana Pérez", status, Money.of(2_300_000)));
        return id;
    }

    /** Test arrangement: one more paid order. */
    public synchronized void paid() {
        order(Status.PAID, Instant.parse("2026-10-15T15:00:00Z"));
    }

    /** Test arrangement: one more withdrawal request still received. */
    public synchronized void withdrawalReceived() {
        receivedWithdrawals++;
    }

    private long count(Status status) {
        return orders.stream().filter(order -> order.status() == status).count();
    }

    @Override
    public synchronized long paidWaiting() {
        return count(Status.PAID);
    }

    @Override
    public synchronized long preparing() {
        return count(Status.PREPARING);
    }

    @Override
    public synchronized long receivedWithdrawals() {
        return receivedWithdrawals;
    }

    @Override
    public synchronized List<RecentOrder> newest(int limit) {
        return orders.stream()
            .sorted(Comparator.comparing(RecentOrder::createdAt).thenComparingLong(RecentOrder::number).reversed())
            .limit(limit)
            .toList();
    }

    @Override
    public synchronized Set<UUID> openAmong(Collection<UUID> orderIds) {
        return orders.stream()
            .filter(order -> orderIds.contains(order.id()) && OPEN.contains(order.status()))
            .map(RecentOrder::id)
            .collect(Collectors.toUnmodifiableSet());
    }
}
