package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ImagePlaceholder;
import com.pijamaparty.store.catalog.domain.ProductImage;
import com.pijamaparty.store.catalog.domain.StorageKey;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One product of the public grid (UC-01): its first photo, if any, the lowest price of its active priced variants and
 * whether none of them has available stock.
 */
public record ProductListingItem(String slug, String name, Optional<ListingImage> image, Money fromPrice,
                                 boolean soldOut) {

    public ProductListingItem {
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(fromPrice, "fromPrice");
    }

    /** The blur-up placeholder and the files of a photo by width (UC-06). */
    public record ListingImage(String placeholder, List<ProductView.ImageFile> files) {

        public ListingImage {
            files = List.copyOf(files);
        }

        public static ListingImage of(StorageKey storageKey, ImagePlaceholder placeholder) {
            return new ListingImage(placeholder.value(), ProductImage.WIDTHS.stream()
                .map(width -> new ProductView.ImageFile(width, storageKey.fileName(width)))
                .toList());
        }
    }
}
