package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.HomeBanner;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** The banner as the back office edits it (UC-32): texts empty until first saved, the photo empty until uploaded. */
public record HomeBannerView(Optional<Texts> texts, Optional<ProductListingItem.ListingImage> image,
                             boolean published, Instant updatedAt) {

    public HomeBannerView {
        Objects.requireNonNull(texts, "texts");
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    static HomeBannerView of(HomeBanner banner) {
        return new HomeBannerView(banner.texts().map(Texts::of),
            banner.photo().map(photo -> ProductListingItem.ListingImage.of(photo.storageKey(), photo.placeholder())),
            banner.published(), banner.updatedAt());
    }

    public record Texts(String title, String body, String linkText, String linkPath) {

        static Texts of(HomeBanner.Texts texts) {
            return new Texts(texts.title(), texts.body(), texts.linkText(), texts.linkPath().value());
        }
    }
}
