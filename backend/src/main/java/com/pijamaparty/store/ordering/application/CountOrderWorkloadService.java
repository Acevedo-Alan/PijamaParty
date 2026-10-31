package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.OrderWorkload;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** T-E8-05 and UC-33: {@link OrderWorkload} for the back office, read as the caller (the administrator). */
@Service
class CountOrderWorkloadService implements OrderWorkload {

    private final OrderStatusCounts counts;
    private final WorkloadStatistics statistics;

    CountOrderWorkloadService(OrderStatusCounts counts, WorkloadStatistics statistics) {
        this.counts = counts;
        this.statistics = statistics;
    }

    @Override
    @Transactional(readOnly = true)
    public long paidWaiting() {
        return counts.count(OrderStatus.PAID);
    }

    @Override
    @Transactional(readOnly = true)
    public long preparing() {
        return counts.count(OrderStatus.PREPARING);
    }

    @Override
    @Transactional(readOnly = true)
    public long receivedWithdrawals() {
        return statistics.receivedWithdrawals();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecentOrder> newest(int limit) {
        return statistics.newest(limit);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> openAmong(Collection<UUID> orderIds) {
        return orderIds.isEmpty() ? Set.of() : statistics.openAmong(orderIds);
    }
}
