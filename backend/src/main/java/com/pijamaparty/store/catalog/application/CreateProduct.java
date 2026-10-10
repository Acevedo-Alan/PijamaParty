package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Set;

/** UC-06: the administrator creates a product; it starts hidden, without variants, with a slug from its name. */
public interface CreateProduct {

    Result execute(Command command);

    record Command(Actor actor, ProductDetailsInput details) {}

    sealed interface Result {

        record Created(ProductView product) implements Result {}

        /** Another product already has the slug this name produces. */
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
