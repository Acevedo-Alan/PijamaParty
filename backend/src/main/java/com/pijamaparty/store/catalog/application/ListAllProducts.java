package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;

/** UC-06: the back office lists every product, published or not, newest first, 50 per page. */
public interface ListAllProducts {

    int PAGE_SIZE = 50;

    Result execute(Query query);

    /** {@code page} counts from 0. */
    record Query(Actor actor, int page) {

        public Query {
            if (page < 0) {
                throw new IllegalArgumentException("page must not be negative");
            }
        }
    }

    sealed interface Result {

        record Listed(List<ProductSummaryView> products, boolean hasMore) implements Result {

            public Listed {
                products = List.copyOf(products);
            }
        }

        record Forbidden() implements Result {}
    }
}
