package com.pijamaparty.store.catalog.application;

import java.util.Objects;

/** UC-30 and UC-32: the home page reads the character banner, shown only when the administrator published it. */
public interface GetHomeBanner {

    Result execute();

    /** A published banner always has its texts and its photo. */
    record PublishedBanner(String title, String body, String linkText, String linkPath,
                           ProductListingItem.ListingImage image) {

        public PublishedBanner {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(linkText, "linkText");
            Objects.requireNonNull(linkPath, "linkPath");
            Objects.requireNonNull(image, "image");
        }
    }

    sealed interface Result {

        record Published(PublishedBanner banner) implements Result {}

        /** Hidden: the home page shows no banner section. */
        record NotPublished() implements Result {}
    }
}
