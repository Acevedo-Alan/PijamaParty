package com.pijamaparty.store.backoffice.adapter.out.ordering;

import com.pijamaparty.store.backoffice.application.CustomerOrderTotals;
import com.pijamaparty.store.ordering.api.CustomerOrderCounts;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reads the customers' order counts through the public API of the ordering module, in the caller's transaction. */
@Component
class OrderingModuleCustomerOrderTotals implements CustomerOrderTotals {

    private final CustomerOrderCounts counts;

    OrderingModuleCustomerOrderTotals(CustomerOrderCounts counts) {
        this.counts = counts;
    }

    @Override
    public Map<UUID, Long> countsOf(Collection<UUID> customerIds) {
        return counts.countsOf(customerIds);
    }
}
