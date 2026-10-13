package com.pijamaparty.store.backoffice.adapter.out.ordering;

import com.pijamaparty.store.backoffice.application.OrderDetails;
import com.pijamaparty.store.ordering.api.AdminOrderDetails;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.AdminOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reads an order through the public API of the ordering module, in the caller's transaction. */
@Component
class OrderingModuleOrderDetails implements OrderDetails {

    private final AdminOrderDetails orders;

    OrderingModuleOrderDetails(AdminOrderDetails orders) {
        this.orders = orders;
    }

    @Override
    public Optional<AdminOrder> find(UUID orderId) {
        return orders.find(orderId);
    }
}
