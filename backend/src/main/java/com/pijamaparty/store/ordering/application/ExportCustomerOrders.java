package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.List;
import java.util.Objects;

/**
 * UC-28 (T-E4-16): the orders section of a customer's data download. Joins the download's transaction, which runs as
 * that customer, so Row Level Security shows only its own orders (V14).
 */
public interface ExportCustomerOrders {

    List<ExportedOrder> execute(Query query);

    record Query(UserId customer) {

        public Query {
            Objects.requireNonNull(customer, "customer");
        }
    }
}
