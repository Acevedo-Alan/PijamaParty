package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.UploadKey;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-06: the administrator uploads a photo of a product, optionally of one of its colors. The server stores it as
 * WebP renditions and a placeholder. A retry with the same upload key and file stores nothing new.
 */
public interface UploadProductImage {

    Result execute(Command command);

    /** {@code color} as the administrator sent it; {@code content} is the uploaded file. */
    record Command(Actor actor, ProductId productId, UploadKey uploadKey, Optional<String> color, byte[] content) {

        public Command {
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(content, "content");
        }
    }

    sealed interface Result {

        record Uploaded(ProductView.ImageView image) implements Result {}

        /** A retry: the photo stored by the first upload with this key. */
        record AlreadyUploaded(ProductView.ImageView image) implements Result {}

        record NotFound() implements Result {}

        /** The product already has {@code Product.MAX_IMAGES} photos. */
        record TooManyImages() implements Result {}

        /** The content is not a JPG, PNG or WebP. */
        record UnsupportedFormat() implements Result {}

        /** Its dimensions are beyond the limits of {@code ImageDimensions}. */
        record TooLarge() implements Result {}

        /** A JPG, PNG or WebP by its first bytes that cannot be decoded. */
        record Unreadable() implements Result {}

        /** The product has no variant of that color. */
        record UnknownColor() implements Result {}

        /** The upload key was used before with another file. */
        record KeyReused() implements Result {}

        record Forbidden() implements Result {}
    }
}
