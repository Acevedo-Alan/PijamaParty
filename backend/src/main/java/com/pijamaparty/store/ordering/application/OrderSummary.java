package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.api.Province;
import java.time.Instant;
import java.util.Objects;

/** UC-18: an order as a row of the back-office list. Personal data (R-05): {@code toString()} hides the buyer. */
public record OrderSummary(OrderId id, long number, Instant createdAt, OrderStatus status, String buyerName,
                           String city, Province province, Money total) {

    public OrderSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(buyerName, "buyerName");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(province, "province");
        Objects.requireNonNull(total, "total");
    }

    @Override
    public String toString() {
        return "OrderSummary[number=" + number + ", status=" + status + ", ***]";
    }
}
