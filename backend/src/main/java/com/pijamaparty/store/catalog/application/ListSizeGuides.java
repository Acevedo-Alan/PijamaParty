package com.pijamaparty.store.catalog.application;

import java.util.List;
import java.util.Objects;

/**
 * RNF-31.5 (T-E7-29): the size guide page of the store, with the guide of every active category that has one, by the
 * categories' position.
 */
public interface ListSizeGuides {

    Result execute();

    /** An active category and its guide. */
    record CategoryGuide(String slug, String name, SizeGuideView guide) {

        public CategoryGuide {
            Objects.requireNonNull(slug, "slug");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(guide, "guide");
        }
    }

    sealed interface Result {

        record Listed(List<CategoryGuide> guides) implements Result {

            public Listed {
                guides = List.copyOf(guides);
            }
        }
    }
}
