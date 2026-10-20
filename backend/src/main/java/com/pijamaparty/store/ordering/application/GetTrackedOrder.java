package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.TrackedOrders.TrackedOrder;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import java.util.Objects;

/** UC-14 (T-E3-08): the order of a tracking link. Run as the buyer that holds the token. */
public interface GetTrackedOrder {

    Result execute(Query query);

    record Query(TrackingToken token) {

        public Query {
            Objects.requireNonNull(token, "token");
        }
    }

    sealed interface Result {

        /** @param payable whether the payment can be retried now (still awaiting it, reservation not over) */
        record Found(TrackedOrder order, boolean payable) implements Result {

            public Found {
                Objects.requireNonNull(order, "order");
            }
        }

        /** No order has this token. */
        record NotFound() implements Result {}
    }
}
