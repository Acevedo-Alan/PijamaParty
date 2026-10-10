package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;

/** What the back office shows of a category (UC-05). */
public record CategoryView(CategoryId id, String name, String slug, int position, boolean active) {

    static CategoryView of(Category category) {
        return new CategoryView(category.id(), category.name().value(), category.slug().value(), category.position(),
            category.active());
    }
}
