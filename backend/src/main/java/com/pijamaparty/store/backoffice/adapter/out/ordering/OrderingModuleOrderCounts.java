package com.pijamaparty.store.backoffice.adapter.out.ordering;

import com.pijamaparty.store.backoffice.application.OrderCounts;
import com.pijamaparty.store.ordering.api.OrderWorkload;
import com.pijamaparty.store.ordering.api.OrderWorkload.RecentOrder;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reads the order work through the public API of the ordering module, in the caller's transaction. */
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

    @Override
    public long preparing() {
        return workload.preparing();
    }

    @Override
    public long receivedWithdrawals() {
        return workload.receivedWithdrawals();
    }

    @Override
    public List<RecentOrder> newest(int limit) {
        return workload.newest(limit);
    }

    @Override
    public Set<UUID> openAmong(Collection<UUID> orderIds) {
        return workload.openAmong(orderIds);
    }
}
