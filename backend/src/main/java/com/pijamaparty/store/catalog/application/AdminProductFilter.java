package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductSearch;
import com.pijamaparty.store.catalog.domain.ProductState;
import com.pijamaparty.store.catalog.domain.ProductStock;
import java.util.Objects;
import java.util.Optional;

/** UC-06 (T-E8-08): which products the back-office list shows; every part optional, all of them combined. */
public record AdminProductFilter(Optional<ProductSearch> search, Optional<CategoryId> category,
                                 Optional<ProductState> state, Optional<StockCondition> stock) {

    public static final AdminProductFilter ALL = new AdminProductFilter(Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.empty());

    public AdminProductFilter {
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(stock, "stock");
    }

    /** The stock filter with the low-stock threshold of "Datos de la tienda" (UC-36) it is measured against. */
    public record StockCondition(ProductStock stock, int threshold) {

        public StockCondition {
            Objects.requireNonNull(stock, "stock");
            if (threshold < 0) {
                throw new IllegalArgumentException("the threshold must not be negative");
            }
        }
    }
}
