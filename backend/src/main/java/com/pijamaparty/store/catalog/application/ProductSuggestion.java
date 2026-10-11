package com.pijamaparty.store.catalog.application;

import java.util.Objects;
import java.util.Optional;

/** A suggestion while the visitor types (UC-26): a listed product with its first photo, if any. */
public record ProductSuggestion(String slug, String name, Optional<ProductListingItem.ListingImage> image) {

    public ProductSuggestion {
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(image, "image");
    }
}
