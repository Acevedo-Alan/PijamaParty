package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.TrackedOrders.TrackedOrder;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** UC-14 (T-E3-08): the order of a tracking link. Run as the buyer that holds the token. */
public interface GetTrackedOrder {

    Result execute(Query query);

    record Query(TrackingToken token) {

        public Query {
            Objects.requireNonNull(token, "token");
        }
    }

    sealed interface Result {

        /**
         * @param payable whether the payment can be retried now (still awaiting it, reservation not over)
         * @param photos the current photo of each line's variant that has one, by variant id (E7)
         */
        record Found(TrackedOrder order, boolean payable, Map<UUID, LinePhotos.Photo> photos) implements Result {

            public Found {
                Objects.requireNonNull(order, "order");
                photos = Map.copyOf(photos);
            }
        }

        /** No order has this token. */
        record NotFound() implements Result {}
    }
}
