package com.pijamaparty.store.catalog.application;

import java.util.List;
import java.util.Objects;

/** UC-26: up to 5 listed products while the visitor types. */
public interface SuggestProducts {

    int LIMIT = 5;

    Result execute(Query query);

    record Query(String text) {

        public Query {
            Objects.requireNonNull(text, "text");
        }
    }

    sealed interface Result {

        /** Empty for a text under 2 characters or without words. */
        record Suggested(List<ProductSuggestion> products) implements Result {

            public Suggested {
                products = List.copyOf(products);
            }
        }
    }
}
