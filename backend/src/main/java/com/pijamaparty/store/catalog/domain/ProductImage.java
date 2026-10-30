package com.pijamaparty.store.catalog.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A photo of a {@link Product} (UC-06), stored as WebP renditions under its {@link StorageKey}. It may belong to one of
 * the product's colors, and then it is shown for every size of that color. Only its product changes it.
 */
public final class ProductImage {

    /**
     * UC-06: the widths of the stored WebP renditions; a photo is never enlarged. 600 px (T-E6-27) is the file a phone
     * picks for the product page's main photo.
     */
    public static final List<Integer> WIDTHS = List.of(400, 600, 800, 1200);

    private final ImageId id;
    private final Color color;
    private final StorageKey storageKey;
    private final ImagePlaceholder placeholder;
    private int position;
    private final UploadKey uploadKey;
    private final ContentHash contentHash;
    private final Instant createdAt;

    private ProductImage(ImageId id, Color color, StorageKey storageKey, ImagePlaceholder placeholder, int position,
                         UploadKey uploadKey, ContentHash contentHash, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.color = color;
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        this.placeholder = Objects.requireNonNull(placeholder, "placeholder");
        if (position < 0) {
            throw new IllegalArgumentException("negative position");
        }
        this.position = position;
        this.uploadKey = Objects.requireNonNull(uploadKey, "uploadKey");
        this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    static ProductImage create(Product.NewImage image, int position, Instant now) {
        return new ProductImage(image.id(), image.color().orElse(null), image.storageKey(), image.placeholder(),
            position, image.uploadKey(), image.contentHash(), now);
    }

    /** Rebuilds a stored photo; only persistence adapters call it. */
    public static ProductImage restore(ImageId id, Optional<Color> color, StorageKey storageKey,
                                       ImagePlaceholder placeholder, int position, UploadKey uploadKey,
                                       ContentHash contentHash, Instant createdAt) {
        return new ProductImage(id, color.orElse(null), storageKey, placeholder, position, uploadKey, contentHash,
            createdAt);
    }

    void moveTo(int newPosition) {
        position = newPosition;
    }

    ProductImage copy() {
        return new ProductImage(id, color, storageKey, placeholder, position, uploadKey, contentHash, createdAt);
    }

    public ImageId id() {
        return id;
    }

    /** Empty for a photo of the whole product. */
    public Optional<Color> color() {
        return Optional.ofNullable(color);
    }

    public StorageKey storageKey() {
        return storageKey;
    }

    public ImagePlaceholder placeholder() {
        return placeholder;
    }

    public int position() {
        return position;
    }

    public UploadKey uploadKey() {
        return uploadKey;
    }

    public ContentHash contentHash() {
        return contentHash;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
