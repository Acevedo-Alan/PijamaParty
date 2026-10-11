package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ImageId;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;

/** UC-06: the administrator sends every photo of a product in the new order. */
public interface ReorderProductImages {

    Result execute(Command command);

    record Command(Actor actor, ProductId productId, List<ImageId> order) {

        public Command {
            order = List.copyOf(order);
        }
    }

    sealed interface Result {

        record Reordered(ProductView product) implements Result {}

        record NotFound() implements Result {}

        /** The order does not name every photo of the product exactly once. */
        record InvalidOrder() implements Result {}

        record Forbidden() implements Result {}
    }
}
