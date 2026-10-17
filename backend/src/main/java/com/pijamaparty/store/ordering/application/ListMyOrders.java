package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;
import java.util.Objects;

/** UC-22 (T-E4-07): the signed-in customer's orders, newest first, 20 per page ({@code GET /api/account/orders}). */
public interface ListMyOrders {

    int PAGE_SIZE = 20;

    Result execute(Query query);

    /** {@code page} counts from 0. */
    record Query(Actor actor, int page) {

        public Query {
            Objects.requireNonNull(actor, "actor");
            if (page < 0) {
                throw new IllegalArgumentException("page must not be negative");
            }
        }
    }

    sealed interface Result {

        record Listed(List<CustomerOrderSummary> orders, boolean hasMore) implements Result {

            public Listed {
                orders = List.copyOf(orders);
            }
        }

        /** The actor is not a customer. */
        record NotSignedIn() implements Result {}
    }
}
