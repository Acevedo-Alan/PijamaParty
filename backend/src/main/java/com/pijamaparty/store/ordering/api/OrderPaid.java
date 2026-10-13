package com.pijamaparty.store.ordering.api;

import com.pijamaparty.store.platform.domain.DomainEvent;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-15, UC-17: an order awaiting payment became {@code PAID}. Published once per order, in the transaction that paid
 * it (the payment's); listeners run in it, so what they enqueue commits or rolls back with the payment.
 */
public record OrderPaid(UUID orderId, long number) implements DomainEvent {

    public OrderPaid {
        Objects.requireNonNull(orderId, "orderId");
    }
}
