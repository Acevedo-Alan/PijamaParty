package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.platform.domain.Actor;

/** UC-05: the administrator reactivates a deactivated category. Activating an active one changes nothing. */
public interface ActivateCategory {

    Result execute(Command command);

    record Command(Actor actor, CategoryId categoryId) {}

    sealed interface Result {

        record Activated(CategoryView category) implements Result {}

        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
