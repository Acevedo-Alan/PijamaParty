package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.HomeBanner;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PublishHomeBannerService implements PublishHomeBanner {

    private final LoadHomeBanner loadHomeBanner;
    private final SaveHomeBanner saveHomeBanner;
    private final Clock clock;

    PublishHomeBannerService(LoadHomeBanner loadHomeBanner, SaveHomeBanner saveHomeBanner, Clock clock) {
        this.loadHomeBanner = loadHomeBanner;
        this.saveHomeBanner = saveHomeBanner;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        HomeBanner banner = loadHomeBanner.lock();
        return switch (banner.publish(clock.instant())) {
            case HomeBanner.PublishOutcome.Incomplete incomplete -> new Result.Incomplete(incomplete.missing());
            case HomeBanner.PublishOutcome.Published published -> {
                saveHomeBanner.save(banner);
                yield new Result.Published(HomeBannerView.of(banner));
            }
        };
    }
}
