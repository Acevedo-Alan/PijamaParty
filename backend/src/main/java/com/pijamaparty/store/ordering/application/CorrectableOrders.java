package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import java.util.Objects;
import java.util.Optional;

/**
 * Outbound read port (UC-17, T-E3-23): locks the order of a tracking link, as the buyer that holds its token, until
 * the caller's transaction ends, so its email is corrected against the status it has then.
 */
public interface CorrectableOrders {

    /** Empty when no order has this token, or Row Level Security hides it from the actor. */
    Optional<CorrectableOrder> lock(TrackingToken token);

    record CorrectableOrder(OrderId id, long number, OrderStatus status) {

        public CorrectableOrder {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(status, "status");
        }
    }
}
