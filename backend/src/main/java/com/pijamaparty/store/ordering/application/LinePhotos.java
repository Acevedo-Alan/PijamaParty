package com.pijamaparty.store.ordering.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Outbound read port (UC-14, E7): the photo the catalog shows now of each sold variant, for the thumbnails of the
 * tracking page. The order keeps what was sold, not how it looked (R-04), so a product that changed its photo shows
 * the new one.
 */
@FunctionalInterface
public interface LinePhotos {

    /** The photo of each variant asked for that has one; unknown variants and variants without photos are left out. */
    Map<UUID, Photo> photosOf(Collection<UUID> variantIds);

    /** The blur-up placeholder and the WebP files of a photo by width (UC-06). */
    record Photo(String placeholder, List<PhotoFile> files) {

        public Photo {
            Objects.requireNonNull(placeholder, "placeholder");
            files = List.copyOf(files);
        }
    }

    record PhotoFile(int width, String fileName) {

        public PhotoFile {
            Objects.requireNonNull(fileName, "fileName");
        }
    }
}
