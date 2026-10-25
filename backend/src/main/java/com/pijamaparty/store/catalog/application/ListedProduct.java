package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One product of the public grid as {@link ProductListingQuery} and {@link ProductSearchQuery} read it: the
 * {@link ProductListingItem} before the clock says whether it is new (D-05).
 */
public record ListedProduct(String slug, String name, Optional<ProductListingItem.ListingImage> image,
                            Optional<ProductListingItem.ListingImage> secondImage, Money fromPrice, boolean soldOut,
                            List<String> colors, Instant createdAt) {

    public ListedProduct {
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(secondImage, "secondImage");
        Objects.requireNonNull(fromPrice, "fromPrice");
        colors = List.copyOf(colors);
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public ProductListingItem toItem(Instant now) {
        return new ProductListingItem(slug, name, image, secondImage, fromPrice, soldOut, colors,
            Product.isNew(createdAt, now));
    }
}
