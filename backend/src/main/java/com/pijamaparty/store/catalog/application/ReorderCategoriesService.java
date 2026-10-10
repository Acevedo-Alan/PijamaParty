package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryOrder;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReorderCategoriesService implements ReorderCategories {

    private final LoadCategories loadCategories;
    private final SaveCategory saveCategory;
    private final Clock clock;

    ReorderCategoriesService(LoadCategories loadCategories, SaveCategory saveCategory, Clock clock) {
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
        List<Category> categories = loadCategories.findAll();
        return switch (CategoryOrder.apply(categories, command.order(), clock.instant())) {
            case CategoryOrder.Outcome.InvalidOrder invalid -> new Result.InvalidOrder();
            case CategoryOrder.Outcome.Reordered reordered -> {
                // Moving a category never changes its slug, so saving cannot collide.
                reordered.changed().forEach(this::save);
                yield new Result.Reordered(categories.stream()
                    .sorted(Comparator.comparingInt(Category::position))
                    .map(CategoryView::of)
                    .toList());
            }
        };
    }

    /**
     * A category deleted while the order was applied fails the whole reorder: the exception rolls back the positions
     * already written, so the categories never end up half reordered.
     */
    private void save(Category category) {
        if (saveCategory.update(category) != SaveCategory.Outcome.SAVED) {
            throw new IllegalStateException("a reordered category was deleted meanwhile");
        }
    }
}
