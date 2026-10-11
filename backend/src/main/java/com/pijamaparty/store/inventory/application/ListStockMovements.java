package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.VariantId;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;
import java.util.Objects;

/** UC-07: the back office shows the stock of a variant and its movements, newest first, 50 per page. */
public interface ListStockMovements {

    int PAGE_SIZE = 50;

    Result execute(Query query);

    /** {@code page} counts from 0. */
    record Query(Actor actor, VariantId variantId, int page) {

        public Query {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(variantId, "variantId");
            if (page < 0) {
                throw new IllegalArgumentException("page must not be negative");
            }
        }
    }

    sealed interface Result {

        record Listed(int onHand, List<StockMovementView> movements, boolean hasMore) implements Result {

            public Listed {
                movements = List.copyOf(movements);
            }
        }

        record UnknownVariant() implements Result {}

        record Forbidden() implements Result {}
    }
}
