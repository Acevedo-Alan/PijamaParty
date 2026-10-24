package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.catalog.domain.ContentHash;
import com.pijamaparty.store.catalog.domain.HomeBanner;
import com.pijamaparty.store.catalog.domain.HomeBannerPhoto;
import com.pijamaparty.store.catalog.domain.UploadKey;
import com.pijamaparty.store.platform.domain.Actor;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database steps of replacing the banner's photo (UC-32), each its own transaction, so the use case converts,
 * writes and removes files outside any transaction, as {@link ProductImageTransactions} does for product photos. A
 * replacement is recorded in the activity log (UC-37).
 */
@Service
class HomeBannerPhotoTransactions {

    private final LoadHomeBanner loadHomeBanner;
    private final SaveHomeBanner saveHomeBanner;
    private final CatalogActivity activity;
    private final Clock clock;

    HomeBannerPhotoTransactions(LoadHomeBanner loadHomeBanner, SaveHomeBanner saveHomeBanner,
                                CatalogActivity activity, Clock clock) {
        this.loadHomeBanner = loadHomeBanner;
        this.saveHomeBanner = saveHomeBanner;
        this.activity = activity;
        this.clock = clock;
    }

    /** Whether the photo can replace the current one, without locking. */
    @Transactional(readOnly = true)
    Step check(UploadKey uploadKey, ContentHash contentHash) {
        HomeBanner banner = loadHomeBanner.load();
        return new Step(banner.checkPhoto(uploadKey, contentHash), HomeBannerView.of(banner));
    }

    /** Replaces the photo under the banner's lock, checking again; with the banner as saved. */
    @Transactional
    Step replace(Actor administrator, HomeBannerPhoto photo) {
        HomeBanner banner = loadHomeBanner.lock();
        HomeBanner.PhotoUpload outcome = banner.replacePhoto(photo, clock.instant());
        if (outcome instanceof HomeBanner.PhotoUpload.Replaced) {
            saveHomeBanner.save(banner);
            activity.record(CatalogEntries.homeBanner(administrator, ActivityAction.HOME_BANNER_PHOTO_REPLACED));
        }
        return new Step(outcome, HomeBannerView.of(banner));
    }

    /** The outcome of a step and the banner as it left it. */
    record Step(HomeBanner.PhotoUpload outcome, HomeBannerView banner) {}
}
