package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeactivateCategoryService implements DeactivateCategory {

    private final LoadCategories loadCategories;
    private final SaveCategory saveCategory;
    private final Clock clock;

    DeactivateCategoryService(LoadCategories loadCategories, SaveCategory saveCategory, Clock clock) {
        this.loadCategories = loadCategories;
        this.saveCategory = saveCategory;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        Optional<Category> found = loadCategories.findById(command.categoryId());
        if (found.isEmpty()) {
            return new Result.NotFound();
        }
        Category category = found.get();
        category.deactivate(clock.instant());
        return switch (saveCategory.update(category)) {
            case SAVED -> new Result.Deactivated(CategoryView.of(category));
            case NOT_FOUND -> new Result.NotFound();
            case SLUG_ALREADY_USED ->
                throw new IllegalStateException("deactivating does not change the slug, so saving cannot collide");
        };
    }
}
