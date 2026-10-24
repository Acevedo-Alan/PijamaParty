package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.platform.domain.Role;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeleteCategoryService implements DeleteCategory {

    private final LoadCategories loadCategories;
    private final RemoveCategory removeCategory;
    private final CatalogActivity activity;

    DeleteCategoryService(LoadCategories loadCategories, RemoveCategory removeCategory, CatalogActivity activity) {
        this.loadCategories = loadCategories;
        this.removeCategory = removeCategory;
        this.activity = activity;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        // Read first: the entry names the category, which is gone once removed.
        Optional<Category> found = loadCategories.findById(command.categoryId());
        if (found.isEmpty()) {
            return new Result.NotFound();
        }
        return switch (removeCategory.remove(command.categoryId())) {
            case REMOVED -> {
                activity.record(CatalogEntries.category(command.actor(), ActivityAction.CATEGORY_DELETED,
                    found.get()));
                yield new Result.Deleted();
            }
            case NOT_FOUND -> new Result.NotFound();
            case HAS_PRODUCTS -> new Result.HasProducts();
        };
    }
}
