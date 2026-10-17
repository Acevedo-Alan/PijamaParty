package com.pijamaparty.store.ordering.application;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Joins the data download's read-only transaction, as the customer itself. */
@Service
class ExportCustomerOrdersService implements ExportCustomerOrders {

    private final CustomerOrderExports exports;

    ExportCustomerOrdersService(CustomerOrderExports exports) {
        this.exports = exports;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<ExportedOrder> execute(Query query) {
        return exports.all(query.customer());
    }
}
