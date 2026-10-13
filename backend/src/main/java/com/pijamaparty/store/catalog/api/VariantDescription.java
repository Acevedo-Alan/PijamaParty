package com.pijamaparty.store.catalog.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A variant as a cart line shows it (UC-08), with the SKU an order keeps of it (UC-13). {@code photo} is the first
 * photo of its color, or else the first photo of the whole product, if any.
 */
public record VariantDescription(String productName, String productSlug, String size, String color, String sku,
                                 Optional<Photo> photo) {

    public VariantDescription {
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(productSlug, "productSlug");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(photo, "photo");
    }

    /** The blur-up placeholder and the WebP files of a photo by width (UC-06), served under {@code /uploads/}. */
    public record Photo(String placeholder, List<PhotoFile> files) {

        public Photo {
            Objects.requireNonNull(placeholder, "placeholder");
            files = List.copyOf(files);
        }
    }

    public record PhotoFile(int width, String fileName) {

        public PhotoFile {
            Objects.requireNonNull(fileName, "fileName");
        }
    }
}
