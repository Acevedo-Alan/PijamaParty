package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Optional;

/** RNF-31.5: the administrator opens the size guide of a category, which may have none yet. */
public interface GetSizeGuide {

    Result execute(Query query);

    record Query(Actor actor, CategoryId categoryId) {}

    sealed interface Result {

        record Found(CategoryView category, Optional<SizeGuideView> guide) implements Result {}

        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
