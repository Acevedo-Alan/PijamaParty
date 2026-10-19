package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.HomeBanner;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UpdateHomeBannerService implements UpdateHomeBanner {

    private final LoadHomeBanner loadHomeBanner;
    private final SaveHomeBanner saveHomeBanner;
    private final Clock clock;

    UpdateHomeBannerService(LoadHomeBanner loadHomeBanner, SaveHomeBanner saveHomeBanner, Clock clock) {
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
        return switch (banner.edit(command.title(), command.body(), command.linkText(), command.linkPath(),
            clock.instant())) {
            case HomeBanner.EditOutcome.Invalid invalid -> new Result.Invalid(invalid.fields());
            case HomeBanner.EditOutcome.Edited edited -> {
                saveHomeBanner.save(banner);
                yield new Result.Updated(HomeBannerView.of(banner));
            }
        };
    }
}
