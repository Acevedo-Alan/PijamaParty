package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.CustomerOrderCounts;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-34 (T-E8-10): {@link CustomerOrderCounts} for the back office, read as the caller (the administrator). */
@Service
class CountCustomerOrdersService implements CustomerOrderCounts {

    private final OrderCountsByCustomer counts;

    CountCustomerOrdersService(OrderCountsByCustomer counts) {
        this.counts = counts;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> countsOf(Collection<UUID> customerIds) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> found = counts.countsOf(customerIds);
        var all = new HashMap<UUID, Long>();
        customerIds.forEach(id -> all.put(id, found.getOrDefault(id, 0L)));
        return Map.copyOf(all);
    }
}
