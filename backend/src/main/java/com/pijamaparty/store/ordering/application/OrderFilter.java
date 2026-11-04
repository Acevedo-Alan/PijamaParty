package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderSearch;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-18: which orders the back-office list shows; every part optional, all of them combined.
 *
 * @param createdFrom the first instant inside, inclusive
 * @param createdBefore the first instant outside, exclusive
 */
public record OrderFilter(Optional<OrderStatus> status, Optional<OrderSearch> search, Optional<Instant> createdFrom,
                          Optional<Instant> createdBefore) {

    public static final OrderFilter ALL = new OrderFilter(Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.empty());

    public OrderFilter {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(createdFrom, "createdFrom");
        Objects.requireNonNull(createdBefore, "createdBefore");
    }

    public static OrderFilter inStatus(OrderStatus status) {
        return new OrderFilter(Optional.of(status), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
