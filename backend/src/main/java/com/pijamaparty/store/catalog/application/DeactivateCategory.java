package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.platform.domain.Actor;

/** UC-05: the administrator deactivates a category, with or without products. Deactivating it again changes nothing. */
public interface DeactivateCategory {

    Result execute(Command command);

    record Command(Actor actor, CategoryId categoryId) {}

    sealed interface Result {

        record Deactivated(CategoryView category) implements Result {}

        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
