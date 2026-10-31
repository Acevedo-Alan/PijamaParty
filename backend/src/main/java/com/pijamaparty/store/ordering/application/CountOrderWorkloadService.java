package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.OrderWorkload;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** T-E8-05: {@link OrderWorkload} for the back office, read as the caller (the administrator). */
@Service
class CountOrderWorkloadService implements OrderWorkload {

    private final OrderStatusCounts counts;

    CountOrderWorkloadService(OrderStatusCounts counts) {
        this.counts = counts;
    }

    @Override
    @Transactional(readOnly = true)
    public long paidWaiting() {
        return counts.count(OrderStatus.PAID);
    }
}
