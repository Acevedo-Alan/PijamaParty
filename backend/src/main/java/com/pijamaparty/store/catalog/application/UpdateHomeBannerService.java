package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.FieldChanges;
import com.pijamaparty.store.catalog.domain.HomeBanner;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UpdateHomeBannerService implements UpdateHomeBanner {

    private final LoadHomeBanner loadHomeBanner;
    private final SaveHomeBanner saveHomeBanner;
    private final CatalogActivity activity;
    private final Clock clock;

    UpdateHomeBannerService(LoadHomeBanner loadHomeBanner, SaveHomeBanner saveHomeBanner, CatalogActivity activity,
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
        Optional<HomeBanner.Texts> previous = banner.texts();
        return switch (banner.edit(command.title(), command.body(), command.linkText(), command.linkPath(),
            clock.instant())) {
            case HomeBanner.EditOutcome.Invalid invalid -> new Result.Invalid(invalid.fields());
            case HomeBanner.EditOutcome.Edited edited -> {
                saveHomeBanner.save(banner);
                FieldChanges changes = changes(previous, banner.texts());
                if (!changes.isEmpty()) {
                    activity.record(CatalogEntries.homeBanner(command.actor(), ActivityAction.HOME_BANNER_UPDATED,
                        changes.list()));
                }
                yield new Result.Updated(HomeBannerView.of(banner));
            }
        };
    }

    /** UC-37: the texts that changed; none before the banner's first edit. */
    private static FieldChanges changes(Optional<HomeBanner.Texts> previous,
                                                  Optional<HomeBanner.Texts> texts) {
        return new FieldChanges()
            .add("title", text(previous, HomeBanner.Texts::title), text(texts, HomeBanner.Texts::title))
            .add("body", text(previous, HomeBanner.Texts::body), text(texts, HomeBanner.Texts::body))
            .add("linkText", text(previous, HomeBanner.Texts::linkText), text(texts, HomeBanner.Texts::linkText))
            .add("linkPath", text(previous, t -> t.linkPath().value()), text(texts, t -> t.linkPath().value()));
    }

    private static String text(Optional<HomeBanner.Texts> texts, Function<HomeBanner.Texts, String> part) {
        return texts.map(part).orElse(null);
    }
}
