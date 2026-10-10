package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.platform.domain.Role;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListAllProductsService implements ListAllProducts {

    private final ProductSummaries summaries;

    ListAllProductsService(ProductSummaries summaries) {
        this.summaries = summaries;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        // One more than a page tells whether another page follows.
        List<ProductSummaryView> rows = summaries.list(Math.multiplyExact(query.page(), PAGE_SIZE), PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        return new Result.Listed(hasMore ? List.copyOf(rows.subList(0, PAGE_SIZE)) : List.copyOf(rows), hasMore);
    }
}
