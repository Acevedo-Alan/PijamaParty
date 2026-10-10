package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;

/** UC-05: the administrator sends every category in the new order. */
public interface ReorderCategories {

    Result execute(Command command);

    record Command(Actor actor, List<CategoryId> order) {

        public Command {
            order = List.copyOf(order);
        }
    }

    sealed interface Result {

        /** Every category, by its new position. */
        record Reordered(List<CategoryView> categories) implements Result {}

        /** The order does not name every category exactly once. */
        record InvalidOrder() implements Result {}

        record Forbidden() implements Result {}
    }
}
