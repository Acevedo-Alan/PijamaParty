package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.StockFilter;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-07 (T-E8-09): the "Stock" screen lists every active variant of every product with its units on hand, reserved and
 * available, fewest available first, 50 per page, filtered by stock level, a search (product name or the variant's
 * SKU) and a category.
 */
public interface ListStockOverview {

    int PAGE_SIZE = 50;

    Result execute(Query query);

    /**
     * {@code page} counts from 0.
     *
     * @param search as typed
     */
    record Query(Actor actor, StockFilter filter, Optional<String> search, Optional<CategoryId> category, int page) {

        public Query {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(filter, "filter");
            Objects.requireNonNull(search, "search");
            Objects.requireNonNull(category, "category");
            if (page < 0) {
                throw new IllegalArgumentException("page must not be negative");
            }
        }
    }

    sealed interface Result {

        record Listed(List<StockOverviewRow> rows, boolean hasMore) implements Result {

            public Listed {
                rows = List.copyOf(rows);
            }
        }

        /** A search of 1 character or over 100; nothing is listed. */
        record InvalidSearch() implements Result {}

        record Forbidden() implements Result {}
    }
}
