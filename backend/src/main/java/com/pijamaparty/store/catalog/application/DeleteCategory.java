package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.platform.domain.Actor;

/** UC-05: the administrator deletes a category without products; one with products can only be deactivated. */
public interface DeleteCategory {

    Result execute(Command command);

    record Command(Actor actor, CategoryId categoryId) {}

    sealed interface Result {

        record Deleted() implements Result {}

        record NotFound() implements Result {}

        /** Not deleted: it has products, so it can only be deactivated. */
        record HasProducts() implements Result {}

        record Forbidden() implements Result {}
    }
}
