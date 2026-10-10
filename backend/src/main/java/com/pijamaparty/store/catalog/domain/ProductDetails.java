package com.pijamaparty.store.catalog.domain;

import java.util.Objects;

/** What the administrator edits of a {@link Product} (UC-06 step 1). */
public record ProductDetails(CategoryId categoryId, ProductName name, Description description, Material material,
                             Season season, Audience audience) {

    public ProductDetails {
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(audience, "audience");
    }
}
