package com.pijamaparty.store.catalog.application;

import java.util.List;
import java.util.Objects;

/** UC-26: the categories a visitor can open, by position (the links shown when a search finds nothing). */
public interface ListShopCategories {

    Result execute();

    record ShopCategory(String slug, String name) {

        public ShopCategory {
            Objects.requireNonNull(slug, "slug");
            Objects.requireNonNull(name, "name");
        }
    }

    sealed interface Result {

        record Listed(List<ShopCategory> categories) implements Result {

            public Listed {
                categories = List.copyOf(categories);
            }
        }
    }
}
