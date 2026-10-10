package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.platform.domain.Actor;

/**
 * UC-06: the administrator publishes a product, which needs an active priced variant. Publishing a published product
 * changes nothing.
 */
public interface PublishProduct {

    Result execute(Command command);

    record Command(Actor actor, ProductId productId) {}

    sealed interface Result {

        record Published(ProductView product) implements Result {}

        record NotFound() implements Result {}

        record NoActivePricedVariant() implements Result {}

        record Forbidden() implements Result {}
    }
}
