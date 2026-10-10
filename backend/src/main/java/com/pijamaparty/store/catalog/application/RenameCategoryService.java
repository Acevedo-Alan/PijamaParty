package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryName;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RenameCategoryService implements RenameCategory {

    private final LoadCategories loadCategories;
    private final SaveCategory saveCategory;
    private final Clock clock;

    RenameCategoryService(LoadCategories loadCategories, SaveCategory saveCategory, Clock clock) {
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
        Optional<CategoryName> name = CategoryName.parse(command.name());
        if (name.isEmpty()) {
            return new Result.InvalidName();
        }
        Optional<Category> found = loadCategories.findById(command.categoryId());
        if (found.isEmpty()) {
            return new Result.NotFound();
        }
        Category category = found.get();
        category.rename(name.get(), clock.instant());
        return switch (saveCategory.update(category)) {
            case SAVED -> new Result.Renamed(CategoryView.of(category));
            case SLUG_ALREADY_USED -> new Result.SlugAlreadyUsed(category.slug().value());
            case NOT_FOUND -> new Result.NotFound();
        };
    }
}
