package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import java.util.Optional;

/** The categories a visitor can open (UC-01): only active ones, by slug. */
final class ShopCategories {

    private ShopCategories() {
    }

    /** The active category with this slug; empty for an unknown or inactive one. A shop has a few dozen at most. */
    static Optional<CategoryId> activeBySlug(LoadCategories categories, String slug) {
        return categories.findAll().stream()
            .filter(category -> category.active() && category.slug().value().equals(slug))
            .map(Category::id)
            .findFirst();
    }
}
