package com.pijamaparty.store.catalog.application;

import java.util.Objects;
import java.util.Optional;

/** UC-02: what the filter panel offers for a category (a slug) or for the whole shop. */
public interface GetFilterOptions {

    Result execute(Query query);

    record Query(Optional<String> category) {

        public Query {
            Objects.requireNonNull(category, "category");
        }
    }

    sealed interface Result {

        record Found(FilterOptions options) implements Result {}

        /** No active category has this slug. */
        record UnknownCategory() implements Result {}
    }
}
