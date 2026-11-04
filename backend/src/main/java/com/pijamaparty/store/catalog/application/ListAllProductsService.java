package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ProductSearch;
import com.pijamaparty.store.platform.domain.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListAllProductsService implements ListAllProducts {

    private final ProductSummaries summaries;
    private final LowStockThreshold threshold;

    ListAllProductsService(ProductSummaries summaries, LowStockThreshold threshold) {
        this.summaries = summaries;
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
        // The threshold is read only when the stock filter needs it.
        var stock = query.stock().map(level -> new AdminProductFilter.StockCondition(level, threshold.units()));
        var filter = new AdminProductFilter(search, query.category(), query.state(), stock);
        // One more than a page tells whether another page follows.
        List<ProductSummaryView> rows = summaries.list(filter, Math.multiplyExact(query.page(), PAGE_SIZE),
            PAGE_SIZE + 1);
        boolean hasMore = rows.size() > PAGE_SIZE;
        return new Result.Listed(hasMore ? List.copyOf(rows.subList(0, PAGE_SIZE)) : List.copyOf(rows), hasMore);
    }
}
