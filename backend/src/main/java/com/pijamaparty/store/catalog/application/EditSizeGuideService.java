package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.SizeGuideForm;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EditSizeGuideService implements EditSizeGuide {

    private final LoadCategories categories;
    private final SaveSizeGuide saveSizeGuide;
    private final Clock clock;

    EditSizeGuideService(LoadCategories categories, SaveSizeGuide saveSizeGuide, Clock clock) {
        this.categories = categories;
        this.saveSizeGuide = saveSizeGuide;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        if (categories.findById(command.categoryId()).isEmpty()) {
            return new Result.NotFound();
        }
        return switch (command.form().toGuide(command.categoryId(), clock.instant())) {
            case SizeGuideForm.Outcome.Invalid invalid -> new Result.InvalidGuide(invalid.fields());
            case SizeGuideForm.Outcome.Valid valid -> switch (saveSizeGuide.save(valid.guide())) {
                case SAVED -> new Result.Saved(SizeGuideView.of(valid.guide()));
                case CATEGORY_NOT_FOUND -> new Result.NotFound();
            };
        };
    }
}
