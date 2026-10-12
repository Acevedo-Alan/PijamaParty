package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListCouponsService implements ListCoupons {

    private final CouponSummaries summaries;

    ListCouponsService(CouponSummaries summaries) {
        this.summaries = summaries;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return new Result.Listed(summaries.listNewestFirst());
    }
}
