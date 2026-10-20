package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.ProductSort;
import com.pijamaparty.store.catalog.domain.Season;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** UC-26: the listed products that match a text, with the filters of UC-02, 24 per page. */
public interface SearchProducts {

    int PAGE_SIZE = ListProducts.PAGE_SIZE;

    Result execute(Query query);

    /**
     * The text and filters as the visitor sent them (see {@link ListProducts.Query}). Without a sort, the most
     * relevant products come first.
     */
    record Query(String text, Optional<String> category, List<String> sizes, List<String> colors,
                 List<String> materials, Set<Season> seasons, Set<Audience> audiences,
                 Optional<Long> minPriceCents, Optional<Long> maxPriceCents, Optional<ProductSort> sort, int page) {

        public Query {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(category, "category");
            sizes = List.copyOf(sizes);
            colors = List.copyOf(colors);
            materials = List.copyOf(materials);
            seasons = Set.copyOf(seasons);
            audiences = Set.copyOf(audiences);
            Objects.requireNonNull(minPriceCents, "minPriceCents");
            Objects.requireNonNull(maxPriceCents, "maxPriceCents");
            Objects.requireNonNull(sort, "sort");
            if (page < 0) {
                throw new IllegalArgumentException("page must not be negative");
            }
        }
    }

    sealed interface Result {

        /** {@code total} counts every match, not only this page (D-11). */
        record Listed(List<ProductListingItem> products, boolean hasMore, long total) implements Result {

            public Listed {
                products = List.copyOf(products);
            }
        }

        /** No active category has this slug. */
        record UnknownCategory() implements Result {}

        /** As {@link ListProducts.Result.InvalidFilter}. */
        record InvalidFilter() implements Result {}

        /** Fewer than 2 characters once trimmed: not a search. */
        record TextTooShort() implements Result {}
    }
}
