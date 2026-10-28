package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.CustomerOrderViews.CustomerOrder;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** UC-22 (T-E4-07): one order of the signed-in customer, with its tracking ({@code GET /api/account/orders/{id}}). */
public interface GetMyOrder {

    Result execute(Query query);

    record Query(Actor actor, OrderId order) {

        public Query {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(order, "order");
        }
    }

    sealed interface Result {

        /** @param photos the current photo of each line's variant that has one, by variant id (E7) */
        record Found(CustomerOrder order, Map<UUID, LinePhotos.Photo> photos) implements Result {

            public Found {
                Objects.requireNonNull(order, "order");
                photos = Map.copyOf(photos);
            }
        }

        /** No such order, or another person's: the answer never tells which. */
        record NotFound() implements Result {}

        /** The actor is not a customer. */
        record NotSignedIn() implements Result {}
    }
}
