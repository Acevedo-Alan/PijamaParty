package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.CustomerOrders;
import com.pijamaparty.store.ordering.api.CustomerOrders.OrderHistory;
import com.pijamaparty.store.ordering.api.CustomerOrders.OrderRow;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Fake of {@link CustomerOrderHistories} with the rules of {@code ordering.api.CustomerOrders}. */
public class InMemoryCustomerOrderHistories implements CustomerOrderHistories {

    private static final Set<Status> BOUGHT = EnumSet.of(Status.PAID, Status.PREPARING, Status.SHIPPED,
        Status.DELIVERED);

    private record Linked(Optional<UUID> customer, OrderRow row) {}

    private final List<Linked> orders = new CopyOnWriteArrayList<>();
    private final AtomicLong numbers = new AtomicLong(1000);

    public OrderRow order(Optional<UUID> customer, Status status, long totalCents, Instant at) {
        var row = new OrderRow(UUID.randomUUID(), numbers.incrementAndGet(), at, status, Money.of(totalCents));
        orders.add(new Linked(customer, row));
        return row;
    }

    @Override
    public OrderHistory historyOf(UUID customerId, int page) {
        List<OrderRow> linked = orders.stream()
            .filter(order -> order.customer().equals(Optional.of(customerId)))
            .map(Linked::row)
            .sorted(Comparator.comparing(OrderRow::createdAt).thenComparing(OrderRow::number).reversed())
            .toList();
        List<OrderRow> bought = linked.stream().filter(row -> BOUGHT.contains(row.status())).toList();
        long size = CustomerOrders.PAGE_SIZE;
        List<OrderRow> rows = linked.stream().skip(page * size).limit(size).toList();
        return new OrderHistory(rows, linked.size() > (page + 1) * size, linked.size(), bought.size(),
            Money.of(bought.stream().mapToLong(row -> row.total().cents()).sum()),
            bought.stream().map(OrderRow::createdAt).max(Comparator.naturalOrder()));
    }
}
