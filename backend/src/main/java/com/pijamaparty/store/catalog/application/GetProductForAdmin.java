package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.platform.domain.Actor;

/** UC-06: the back office opens a product, published or not, with its variants. */
public interface GetProductForAdmin {

    Result execute(Query query);

    record Query(Actor actor, ProductId productId) {}

    sealed interface Result {

        record Found(ProductView product) implements Result {}

        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
