package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.SizeGuide;
import com.pijamaparty.store.catalog.domain.SizeGuideForm;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EditSizeGuideService implements EditSizeGuide {

    private final LoadCategories categories;
    private final LoadSizeGuides loadSizeGuides;
    private final SaveSizeGuide saveSizeGuide;
    private final CatalogActivity activity;
    private final Clock clock;

    EditSizeGuideService(LoadCategories categories, LoadSizeGuides loadSizeGuides, SaveSizeGuide saveSizeGuide,
                         CatalogActivity activity, Clock clock) {
        this.categories = categories;
        this.loadSizeGuides = loadSizeGuides;
        this.saveSizeGuide = saveSizeGuide;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        Optional<Category> category = categories.findById(command.categoryId());
        if (category.isEmpty()) {
            return new Result.NotFound();
        }
        return switch (command.form().toGuide(command.categoryId(), clock.instant())) {
            case SizeGuideForm.Outcome.Invalid invalid -> new Result.InvalidGuide(invalid.fields());
            case SizeGuideForm.Outcome.Valid valid -> {
                Optional<SizeGuide> previous = loadSizeGuides.findByCategory(command.categoryId());
                yield switch (saveSizeGuide.save(valid.guide())) {
                    case SAVED -> {
                        if (previous.isEmpty() || !previous.get().sameContentAs(valid.guide())) {
                            activity.record(CatalogEntries.category(command.actor(), ActivityAction.SIZE_GUIDE_SAVED,
                                category.get()));
                        }
                        yield new Result.Saved(SizeGuideView.of(valid.guide()));
                    }
                    case CATEGORY_NOT_FOUND -> new Result.NotFound();
                };
            }
        };
    }
}
