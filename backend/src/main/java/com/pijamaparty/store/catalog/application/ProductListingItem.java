package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ImagePlaceholder;
import com.pijamaparty.store.catalog.domain.ProductImage;
import com.pijamaparty.store.catalog.domain.StorageKey;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One product of the public grid (UC-01): its first photo, if any, and the one after it, shown on hover (T-E7-14),
 * the lowest price of its active priced variants, whether none of them has available stock, the colors of those with
 * available stock in display order (D-04), the sizes of all of them in display order, each saying whether it has
 * available stock (T-E7-35), and whether it was created in the last 30 days (D-05).
 */
public record ProductListingItem(String slug, String name, Optional<ListingImage> image,
                                 Optional<ListingImage> secondImage, Money fromPrice, boolean soldOut,
                                 List<String> colors, List<SizeOffer> sizes, boolean isNew) {

    public ProductListingItem {
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(secondImage, "secondImage");
        Objects.requireNonNull(fromPrice, "fromPrice");
        colors = List.copyOf(colors);
        sizes = List.copyOf(sizes);
    }

    /** A size of the product's active priced variants; {@code inStock} when one of them has available stock. */
    public record SizeOffer(String size, boolean inStock) {

        public SizeOffer {
            Objects.requireNonNull(size, "size");
        }
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
