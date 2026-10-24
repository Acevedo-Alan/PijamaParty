package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.ActivityValues;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
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
    private final CatalogActivity activity;
    private final Clock clock;

    ReorderCategoriesService(LoadCategories loadCategories, SaveCategory saveCategory, CatalogActivity activity,
                             Clock clock) {
        this.loadCategories = loadCategories;
        this.saveCategory = saveCategory;
        this.activity = activity;
        this.clock = clock;
    }

    /** The names in the given order, as the entry shows them (UC-37). */
    private static String names(List<Category> categories) {
        return ActivityValues.list(categories.stream().map(category -> category.name().value()).toList());
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        List<Category> categories = loadCategories.findAll();
        String before = names(categories);
        return switch (CategoryOrder.apply(categories, command.order(), clock.instant())) {
            case CategoryOrder.Outcome.InvalidOrder invalid -> new Result.InvalidOrder();
            case CategoryOrder.Outcome.Reordered reordered -> {
                // Moving a category never changes its slug, so saving cannot collide.
                reordered.changed().forEach(this::save);
                List<Category> ordered = categories.stream()
                    .sorted(Comparator.comparingInt(Category::position))
                    .toList();
                if (!reordered.changed().isEmpty()) {
                    activity.record(CatalogEntries.entry(command.actor(), ActivityAction.CATEGORIES_REORDERED,
                        ActivitySubject.of(SubjectType.CATEGORY), List.of(FieldChange.of("order", before,
                            names(ordered)))));
                }
                yield new Result.Reordered(ordered.stream().map(CategoryView::of).toList());
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
