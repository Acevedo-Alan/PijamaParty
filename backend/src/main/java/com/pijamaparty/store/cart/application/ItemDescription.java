package com.pijamaparty.store.cart.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A cart line's variant as the panel shows it (UC-08): its product's name and slug, its size and color, and a photo,
 * the first of its color or else the first of the whole product. Its SKU goes into the order snapshot (UC-13).
 */
public record ItemDescription(String productName, String productSlug, String size, String color, String sku,
                              Optional<Photo> photo) {

    public ItemDescription {
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(productSlug, "productSlug");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(photo, "photo");
    }

    /** The blur-up placeholder and the WebP files of a photo by width (UC-06). */
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
