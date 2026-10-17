package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.Objects;

/** UC-22: an order as a row of the customer's "Mis pedidos". */
public record CustomerOrderSummary(OrderId id, long number, Instant createdAt, OrderStatus status, Money total) {

    public CustomerOrderSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(total, "total");
    }
}
