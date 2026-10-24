package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.platform.domain.Role;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeleteSizeGuideService implements DeleteSizeGuide {

    private final LoadCategories categories;
    private final LoadSizeGuides loadSizeGuides;
    private final RemoveSizeGuide removeSizeGuide;
    private final CatalogActivity activity;

    DeleteSizeGuideService(LoadCategories categories, LoadSizeGuides loadSizeGuides, RemoveSizeGuide removeSizeGuide,
                           CatalogActivity activity) {
        this.categories = categories;
        this.loadSizeGuides = loadSizeGuides;
        this.removeSizeGuide = removeSizeGuide;
        this.activity = activity;
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
        boolean hadGuide = loadSizeGuides.findByCategory(command.categoryId()).isPresent();
        removeSizeGuide.remove(command.categoryId());
        if (hadGuide) {
            activity.record(CatalogEntries.category(command.actor(), ActivityAction.SIZE_GUIDE_DELETED,
                category.get()));
        }
        return new Result.Deleted();
    }
}
