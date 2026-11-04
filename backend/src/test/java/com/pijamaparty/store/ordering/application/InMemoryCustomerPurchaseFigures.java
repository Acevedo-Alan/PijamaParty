package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake of {@link CustomerPurchaseFigures}: the orders the test gives, each linked to a customer or a guest's. */
public class InMemoryCustomerPurchaseFigures implements CustomerPurchaseFigures {

    private record Order(Optional<UUID> customer, OrderStatus status, long totalCents, Instant at) {}

    private final List<Order> orders = new CopyOnWriteArrayList<>();

    public void order(Optional<UUID> customer, OrderStatus status, long totalCents, Instant at) {
        orders.add(new Order(customer, status, totalCents, at));
    }

    @Override
    public Figures figuresOf(UserId customer) {
        List<Order> linked = orders.stream()
            .filter(order -> order.customer().equals(Optional.of(customer.value())))
            .toList();
        List<Order> bought = linked.stream().filter(order -> order.status().isBought()).toList();
        return new Figures(linked.size(), bought.size(),
            Money.of(bought.stream().mapToLong(Order::totalCents).sum()),
            bought.stream().map(Order::at).max(Comparator.naturalOrder()));
    }
}
