package com.pijamaparty.store.backoffice.adapter.out.ordering;

import com.pijamaparty.store.backoffice.application.CustomerOrderHistories;
import com.pijamaparty.store.ordering.api.CustomerOrders;
import com.pijamaparty.store.ordering.api.CustomerOrders.OrderHistory;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reads a customer's orders through the public API of the ordering module, in the caller's transaction. */
@Component
class OrderingModuleCustomerOrderHistories implements CustomerOrderHistories {

    private final CustomerOrders orders;

    OrderingModuleCustomerOrderHistories(CustomerOrders orders) {
        this.orders = orders;
    }

    @Override
    public OrderHistory historyOf(UUID customerId, int page) {
        return orders.historyOf(customerId, page);
    }
}
