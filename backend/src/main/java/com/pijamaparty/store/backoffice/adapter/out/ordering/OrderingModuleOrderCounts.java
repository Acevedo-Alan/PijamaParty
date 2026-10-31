package com.pijamaparty.store.backoffice.adapter.out.ordering;

import com.pijamaparty.store.backoffice.application.OrderCounts;
import com.pijamaparty.store.ordering.api.OrderWorkload;
import org.springframework.stereotype.Component;

/** Reads the order counts through the public API of the ordering module, in the caller's transaction. */
@Component
class OrderingModuleOrderCounts implements OrderCounts {

    private final OrderWorkload workload;

    OrderingModuleOrderCounts(OrderWorkload workload) {
        this.workload = workload;
    }

    @Override
    public long paidWaiting() {
        return workload.paidWaiting();
    }
}
