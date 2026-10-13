package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.AdminOrder;
import java.util.Optional;
import java.util.UUID;

/** Outbound read port (UC-18): an order as the ordering module shows it to the administrator. */
public interface OrderDetails {

    /** Empty when there is no such order. */
    Optional<AdminOrder> find(UUID orderId);
}
