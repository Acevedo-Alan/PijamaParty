package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ImageId;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.platform.domain.Actor;

/** UC-06: the administrator deletes a photo of a product; its files are removed after the change is committed. */
public interface DeleteProductImage {

    Result execute(Command command);

    record Command(Actor actor, ProductId productId, ImageId imageId) {}

    sealed interface Result {

        record Deleted() implements Result {}

        /** No such product, or the product has no such photo. */
        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
