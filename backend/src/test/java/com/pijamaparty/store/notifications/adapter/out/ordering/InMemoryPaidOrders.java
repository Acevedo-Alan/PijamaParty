package com.pijamaparty.store.notifications.adapter.out.ordering;

import com.pijamaparty.store.ordering.api.PaidOrders;
import com.pijamaparty.store.ordering.api.TrackingLinks;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Fake of {@code ordering.api}'s {@link PaidOrders} and {@link TrackingLinks}: arranged orders, numbered links. */
class InMemoryPaidOrders implements PaidOrders, TrackingLinks {

    private final Map<UUID, PaidOrder> orders = new ConcurrentHashMap<>();
    private final List<UUID> linked = new CopyOnWriteArrayList<>();
    private final AtomicInteger sequence = new AtomicInteger();

    /**
     * Test arrangement: order 1001 of Ana María Pérez, 2 "Pijama Luna" at $ 20.000 and 1 "Camisón <Sol>" at
     * $ 10.000, coupon VERANO10 ($ 5.000 off), shipping $ 3.000, total $ 48.000, to San Martín 1234, 3rd floor,
     * apartment B, Santa Fe.
     */
    PaidOrder paid() {
        return paid(Money.of(300_000), Optional.of("VERANO10"), Money.of(500_000));
    }

    PaidOrder paid(Money shipping, Optional<String> couponCode, Money discount) {
        Money subtotal = Money.of(5_000_000);
        var order = new PaidOrder(UUID.randomUUID(), 1001, "Ana María Pérez", "ana@example.com",
            List.of(new Line("Pijama Luna", "M", "Rosa", 2, Money.of(2_000_000), Money.of(4_000_000)),
                new Line("Camisón <Sol>", "S", "Gris", 1, Money.of(1_000_000), Money.of(1_000_000))),
            subtotal, discount, couponCode, shipping, subtotal.minus(discount).plus(shipping),
            new Address("San Martín", "1234", Optional.of("3"), Optional.of("B"), "Santa Fe", "SANTA_FE",
                "S3000ABC"));
        orders.put(order.orderId(), order);
        return order;
    }

    @Override
    public Optional<PaidOrder> find(UUID orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    /** Tokens {@code link-token-1}, {@code link-token-2} ... for an arranged order. */
    @Override
    public TrackingToken issue(UUID orderId) {
        if (!orders.containsKey(orderId)) {
            throw new IllegalArgumentException("no order " + orderId);
        }
        linked.add(orderId);
        return new TrackingToken("link-token-" + sequence.incrementAndGet());
    }

    /** Test inspection: the orders a link was issued for, once per link. */
    List<UUID> linked() {
        return List.copyOf(linked);
    }
}
