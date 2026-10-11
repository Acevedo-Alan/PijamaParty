package com.pijamaparty.store.catalog.application;

import java.util.Objects;

/** UC-03: a visitor opens a listed product by its slug. */
public interface GetProductDetail {

    Result execute(Query query);

    /** {@code slug} as the visitor sent it. */
    record Query(String slug) {

        public Query {
            Objects.requireNonNull(slug, "slug");
        }
    }

    sealed interface Result {

        record Found(ProductDetail product) implements Result {}

        /** No product has this slug, or visitors do not see it: hidden, of an inactive category or unsellable. */
        record NotFound() implements Result {}
    }
}
