package com.pijamaparty.store.catalog.domain;

import java.util.Objects;

/**
 * The photo of the {@link HomeBanner} (UC-32), stored as the WebP renditions of UC-06 under its {@link StorageKey},
 * with the upload key and the SHA-256 of the uploaded file that tell a retry from a reused key.
 */
public record HomeBannerPhoto(StorageKey storageKey, ImagePlaceholder placeholder, UploadKey uploadKey,
                              ContentHash contentHash) {

    public HomeBannerPhoto {
        Objects.requireNonNull(storageKey, "storageKey");
        Objects.requireNonNull(placeholder, "placeholder");
        Objects.requireNonNull(uploadKey, "uploadKey");
        Objects.requireNonNull(contentHash, "contentHash");
    }
}
