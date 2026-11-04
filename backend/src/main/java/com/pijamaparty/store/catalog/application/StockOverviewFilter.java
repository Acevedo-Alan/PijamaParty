package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductSearch;
import com.pijamaparty.store.catalog.domain.StockFilter;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-07 (T-E8-09): which variants the "Stock" screen lists; the search and the category are optional, all combined.
 *
 * @param threshold the low-stock threshold of the store's settings (UC-36) that {@link StockFilter#LOW} uses
 */
public record StockOverviewFilter(StockFilter stock, int threshold, Optional<ProductSearch> search,
                                  Optional<CategoryId> category) {

    public StockOverviewFilter {
        Objects.requireNonNull(stock, "stock");
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(category, "category");
        if (threshold < 0) {
            throw new IllegalArgumentException("the threshold must not be negative");
        }
    }

    public static StockOverviewFilter of(StockFilter stock, int threshold) {
        return new StockOverviewFilter(stock, threshold, Optional.empty(), Optional.empty());
    }
}
