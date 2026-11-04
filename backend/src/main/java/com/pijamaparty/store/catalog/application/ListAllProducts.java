package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductState;
import com.pijamaparty.store.catalog.domain.ProductStock;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-06: the back office lists every product, published or not, newest first, 50 per page, filtered by a search (name
 * or SKU), a category, a state and a stock level, each optional and all combined (T-E8-08).
 */
public interface ListAllProducts {

    int PAGE_SIZE = 50;

    Result execute(Query query);

    /**
     * {@code page} counts from 0.
     *
     * @param search as typed
     */
    record Query(Actor actor, Optional<String> search, Optional<CategoryId> category, Optional<ProductState> state,
                 Optional<ProductStock> stock, int page) {

        public Query {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(search, "search");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(stock, "stock");
            if (page < 0) {
                throw new IllegalArgumentException("page must not be negative");
            }
        }

        /** Every product: the list as it was before T-E8-08. */
        public Query(Actor actor, int page) {
            this(actor, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), page);
        }
    }

    sealed interface Result {

        record Listed(List<ProductSummaryView> products, boolean hasMore) implements Result {

            public Listed {
                products = List.copyOf(products);
            }
        }

        /** A search of 1 character or over 100; nothing is listed. */
        record InvalidSearch() implements Result {}

        record Forbidden() implements Result {}
    }
}
