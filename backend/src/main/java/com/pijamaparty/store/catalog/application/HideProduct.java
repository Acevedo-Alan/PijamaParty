package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.platform.domain.Actor;

/** UC-06: the administrator takes a product out of the shop. Hiding a hidden product changes nothing. */
public interface HideProduct {

    Result execute(Command command);

    record Command(Actor actor, ProductId productId) {}

    sealed interface Result {

        record Hidden(ProductView product) implements Result {}

        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
