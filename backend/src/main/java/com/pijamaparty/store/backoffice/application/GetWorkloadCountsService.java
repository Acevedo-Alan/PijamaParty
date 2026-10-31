package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetWorkloadCountsService implements GetWorkloadCounts {

    private final OrderCounts orderCounts;

    GetWorkloadCountsService(OrderCounts orderCounts) {
        this.orderCounts = orderCounts;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return new Result.Counted(orderCounts.paidWaiting());
    }
}
