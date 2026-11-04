package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ProductSearch;
import com.pijamaparty.store.platform.domain.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListStockOverviewService implements ListStockOverview {

    private final StockOverviewPages pages;
    private final LowStockThreshold threshold;

    ListStockOverviewService(StockOverviewPages pages, LowStockThreshold threshold) {
        this.pages = pages;
        this.threshold = threshold;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        Optional<ProductSearch> search;
        switch (ProductSearch.parse(query.search().orElse(""))) {
            case ProductSearch.Parsed.Invalid invalid -> {
                return new Result.InvalidSearch();
            }
            case ProductSearch.Parsed.None none -> search = Optional.empty();
            case ProductSearch.Parsed.Valid valid -> search = Optional.of(valid.search());
        }
        var filter = new StockOverviewFilter(query.filter(), threshold.units(), search, query.category());
        // One more than a page tells whether another page follows.
        List<StockOverviewRow> rows = pages.page(filter, Math.multiplyExact(query.page(), PAGE_SIZE), PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        return new Result.Listed(hasMore ? rows.subList(0, PAGE_SIZE) : rows, hasMore);
    }
}
