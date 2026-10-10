package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CreateCategoryService implements CreateCategory {

    private final LoadCategories loadCategories;
    private final SaveCategory saveCategory;
    private final IdGenerator ids;
    private final Clock clock;

    CreateCategoryService(LoadCategories loadCategories, SaveCategory saveCategory, IdGenerator ids, Clock clock) {
        this.loadCategories = loadCategories;
        this.saveCategory = saveCategory;
        this.ids = ids;
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
        Category category = Category.create(CategoryId.of(ids.newId()), name.get(), loadCategories.findAll(),
            clock.instant());
        return switch (saveCategory.add(category)) {
            case SAVED -> new Result.Created(CategoryView.of(category));
            case SLUG_ALREADY_USED -> new Result.SlugAlreadyUsed(category.slug().value());
            case NOT_FOUND -> throw new IllegalStateException("adding a category never misses a stored one");
        };
    }
}
