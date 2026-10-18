package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.SizeGuideForm;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Set;

/** RNF-31.5: the administrator creates or replaces the size guide of a category, whole. */
public interface EditSizeGuide {

    Result execute(Command command);

    /** The guide as typed: the use case trims and validates it. */
    record Command(Actor actor, CategoryId categoryId, SizeGuideForm form) {}

    sealed interface Result {

        record Saved(SizeGuideView guide) implements Result {}

        record NotFound() implements Result {}

        /** Nothing changed: these parts break a rule. */
        record InvalidGuide(Set<SizeGuideForm.Field> fields) implements Result {

            public InvalidGuide {
                fields = Set.copyOf(fields);
            }
        }

        record Forbidden() implements Result {}
    }
}
