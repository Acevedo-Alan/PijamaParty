package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;

/** UC-05: the back office lists every category, active or not, by position. */
public interface ListAllCategories {

    Result execute(Query query);

    record Query(Actor actor) {}

    sealed interface Result {

        record Listed(List<CategoryView> categories) implements Result {}

        record Forbidden() implements Result {}
    }
}
