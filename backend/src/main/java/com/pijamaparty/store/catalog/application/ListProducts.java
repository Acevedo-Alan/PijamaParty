package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.ProductSort;
import com.pijamaparty.store.catalog.domain.Season;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** UC-01, UC-02: the public grid of listed products, filtered and sorted, 24 per page. */
public interface ListProducts {

    int PAGE_SIZE = 24;

    Result execute(Query query);

    /**
     * The filters as the visitor sent them: {@code category} is a slug, sizes, colors and materials are texts, prices
     * are in cents. {@code page} counts from 0.
     */
    record Query(Optional<String> category, List<String> sizes, List<String> colors, List<String> materials,
                 Set<Season> seasons, Set<Audience> audiences, Optional<Long> minPriceCents,
                 Optional<Long> maxPriceCents, ProductSort sort, int page) {

        public Query {
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

        record Listed(List<ProductListingItem> products, boolean hasMore) implements Result {

            public Listed {
                products = List.copyOf(products);
            }
        }

        /** No active category has this slug. */
        record UnknownCategory() implements Result {}

        /** A size, color or material that cannot exist, too many values, a negative price or min above max. */
        record InvalidFilter() implements Result {}
    }
}
