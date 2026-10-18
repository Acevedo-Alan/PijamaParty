package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.platform.domain.Actor;

/** RNF-31.5: the administrator deletes the size guide of a category; its product pages stop showing the link. */
public interface DeleteSizeGuide {

    Result execute(Command command);

    record Command(Actor actor, CategoryId categoryId) {}

    sealed interface Result {

        /** Also when the category had no guide. */
        record Deleted() implements Result {}

        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
