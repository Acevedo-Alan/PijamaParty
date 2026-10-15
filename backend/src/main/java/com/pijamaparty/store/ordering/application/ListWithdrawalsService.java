package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Role;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListWithdrawalsService implements ListWithdrawals {

    private final WithdrawalPages withdrawalPages;

    ListWithdrawalsService(WithdrawalPages withdrawalPages) {
        this.withdrawalPages = withdrawalPages;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        // One more than a page tells whether another page follows.
        List<WithdrawalSummary> rows = withdrawalPages.page(query.status(),
            Math.multiplyExact(query.page(), PAGE_SIZE), PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        return new Result.Listed(hasMore ? rows.subList(0, PAGE_SIZE) : rows, hasMore);
    }
}
