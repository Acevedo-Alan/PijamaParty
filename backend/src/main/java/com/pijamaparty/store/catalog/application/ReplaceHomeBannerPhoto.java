package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.UploadKey;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/**
 * UC-32: the administrator uploads the banner's photo, replacing the current one. It is converted as product photos are
 * (UC-06); the old files are deleted once the new photo is saved. A retry with the same upload key and file stores
 * nothing new.
 */
public interface ReplaceHomeBannerPhoto {

    Result execute(Command command);

    /** {@code content} is the uploaded file. */
    record Command(Actor actor, UploadKey uploadKey, byte[] content) {

        public Command {
            Objects.requireNonNull(uploadKey, "uploadKey");
            Objects.requireNonNull(content, "content");
        }
    }

    sealed interface Result {

        record Replaced(HomeBannerView banner) implements Result {}

        /** A retry: the banner already has the photo of this upload key and file. */
        record AlreadyUploaded(HomeBannerView banner) implements Result {}

        /** The content is not a JPG, PNG or WebP. */
        record UnsupportedFormat() implements Result {}

        /** Its dimensions are beyond the limits of {@code ImageDimensions}. */
        record TooLarge() implements Result {}

        /** A JPG, PNG or WebP by its first bytes that cannot be decoded. */
        record Unreadable() implements Result {}

        /** The upload key is the current photo's, sent with another file. */
        record KeyReused() implements Result {}

        record Forbidden() implements Result {}
    }
}
