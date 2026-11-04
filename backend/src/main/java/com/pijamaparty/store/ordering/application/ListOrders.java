package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Actor;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-18: the back office lists the orders, newest first, 50 per page, filtered by status, by a search (an order number
 * or the buyer's name or email) and by the store dates they were created on, each optional and all combined (T-E8-06).
 */
public interface ListOrders {

    int PAGE_SIZE = 50;

    Result execute(Query query);

    /**
     * {@code page} counts from 0.
     *
     * @param search as typed; personal data, never logged
     * @param from a store date, inclusive
     * @param to a store date, inclusive
     */
    record Query(Actor actor, Optional<OrderStatus> status, Optional<String> search, Optional<LocalDate> from,
                 Optional<LocalDate> to, int page) {

        public Query {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(search, "search");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            if (page < 0) {
                throw new IllegalArgumentException("page must not be negative");
            }
        }

        /** Every order, or those in {@code status}: the list as it was before T-E8-06. */
        public Query(Actor actor, Optional<OrderStatus> status, int page) {
            this(actor, status, Optional.empty(), Optional.empty(), Optional.empty(), page);
        }

        @Override
        public String toString() {
            return "Query[actor=" + actor + ", status=" + status + ", search=<hidden>, from=" + from + ", to=" + to
                + ", page=" + page + "]";
        }
    }

    sealed interface Result {

        record Listed(List<OrderSummary> orders, boolean hasMore) implements Result {

            public Listed {
                orders = List.copyOf(orders);
            }
        }

        /** A search of 1 character or over 100 that is not an order number; nothing is listed. */
        record InvalidSearch() implements Result {}

        /** {@code from} after {@code to}; nothing is listed. */
        record InvalidPeriod() implements Result {}

        record Forbidden() implements Result {}
    }
}
