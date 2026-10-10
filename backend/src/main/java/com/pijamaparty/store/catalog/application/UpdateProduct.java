package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Set;

/** UC-06: the administrator edits the details of a product; a new name regenerates the slug. */
public interface UpdateProduct {

    Result execute(Command command);

    record Command(Actor actor, ProductId productId, ProductDetailsInput details) {}

    sealed interface Result {

        record Updated(ProductView product) implements Result {}

        record NotFound() implements Result {}

        /** Another product already has the slug the new name produces. */
        record SlugAlreadyUsed(String slug) implements Result {}

        record InvalidDetails(Set<ProductDetailsInput.Field> fields) implements Result {

            public InvalidDetails {
                fields = Set.copyOf(fields);
            }
        }

        record UnknownCategory() implements Result {}

        record Forbidden() implements Result {}
    }
}
