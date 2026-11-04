package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.CustomerOrders.OrderRow;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

class InMemoryCustomerOrderHistoriesContractTest
    extends CustomerOrderHistoriesContractTest<InMemoryCustomerOrderHistories> {

    @Override
    protected InMemoryCustomerOrderHistories createSubject() {
        return new InMemoryCustomerOrderHistories();
    }

    @Override
    protected OrderRow givenOrder(Optional<UUID> customer, Status status, long totalCents, Instant at) {
        return subject.order(customer, status, totalCents, at);
    }
}
