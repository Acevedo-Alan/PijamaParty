package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.PaidOrders.PaidOrder;
import com.pijamaparty.store.ordering.domain.OrderId;
import java.util.Optional;

/** Outbound read port (UC-17): an order as the payment-confirmed emails tell it, in the caller's transaction. */
public interface PaidOrderDetails {

    /** Empty when there is no such order, or Row Level Security hides it from the actor. */
    Optional<PaidOrder> detailsOf(OrderId order);
}
