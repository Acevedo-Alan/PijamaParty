package com.pijamaparty.store.catalog.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetHomeBannerService implements GetHomeBanner {

    private final LoadHomeBanner loadHomeBanner;

    GetHomeBannerService(LoadHomeBanner loadHomeBanner) {
        this.loadHomeBanner = loadHomeBanner;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute() {
        HomeBannerView banner = HomeBannerView.of(loadHomeBanner.load());
        if (!banner.published()) {
            return new Result.NotPublished();
        }
        // HomeBanner never publishes without its texts and its photo.
        HomeBannerView.Texts texts = banner.texts().orElseThrow();
        return new Result.Published(new PublishedBanner(texts.title(), texts.body(), texts.linkText(),
            texts.linkPath(), banner.image().orElseThrow()));
    }
}
