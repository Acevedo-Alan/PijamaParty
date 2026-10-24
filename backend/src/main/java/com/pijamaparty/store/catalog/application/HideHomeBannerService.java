package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.catalog.domain.HomeBanner;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class HideHomeBannerService implements HideHomeBanner {

    private final LoadHomeBanner loadHomeBanner;
    private final SaveHomeBanner saveHomeBanner;
    private final CatalogActivity activity;
    private final Clock clock;

    HideHomeBannerService(LoadHomeBanner loadHomeBanner, SaveHomeBanner saveHomeBanner, CatalogActivity activity,
                          Clock clock) {
        this.loadHomeBanner = loadHomeBanner;
        this.saveHomeBanner = saveHomeBanner;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        HomeBanner banner = loadHomeBanner.lock();
        boolean wasPublished = banner.published();
        banner.hide(clock.instant());
        saveHomeBanner.save(banner);
        if (wasPublished) {
            activity.record(CatalogEntries.homeBanner(command.actor(), ActivityAction.HOME_BANNER_HIDDEN));
        }
        return new Result.Hidden(HomeBannerView.of(banner));
    }
}
