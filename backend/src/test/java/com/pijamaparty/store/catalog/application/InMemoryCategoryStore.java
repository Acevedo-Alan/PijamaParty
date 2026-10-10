package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.platform.testing.InMemoryStore;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link LoadCategories}, {@link SaveCategory} and {@link RemoveCategory}, with the database's rules. */
public class InMemoryCategoryStore implements LoadCategories, SaveCategory, RemoveCategory {

    private final InMemoryStore<CategoryId, Category> categories = InMemoryStore.copying(Category::id, Category::copy);
    private final Set<CategoryId> withProducts = ConcurrentHashMap.newKeySet();

    @Override
    public Optional<Category> findById(CategoryId id) {
        return categories.findById(id);
    }

    @Override
    public List<Category> findAll() {
        return categories.findAll().stream()
            .sorted(Comparator.comparingInt(Category::position).thenComparing(Category::createdAt)
                .thenComparing(category -> category.id().value()))
            .toList();
    }

    @Override
    public synchronized SaveCategory.Outcome add(Category category) {
        return write(category);
    }

    @Override
    public synchronized SaveCategory.Outcome update(Category category) {
        if (categories.findById(category.id()).isEmpty()) {
            return SaveCategory.Outcome.NOT_FOUND;
        }
        return write(category);
    }

    /** Test arrangement: stores the category as it is, like {@link #add} without the result. */
    public void save(Category category) {
        if (add(category) != SaveCategory.Outcome.SAVED) {
            throw new IllegalArgumentException("slug already used");
        }
    }

    private SaveCategory.Outcome write(Category category) {
        boolean slugTaken = !categories.findAll(other ->
            other.slug().equals(category.slug()) && !other.id().equals(category.id())).isEmpty();
        if (slugTaken) {
            return SaveCategory.Outcome.SLUG_ALREADY_USED;
        }
        categories.save(category);
        return SaveCategory.Outcome.SAVED;
    }

    @Override
    public synchronized RemoveCategory.Outcome remove(CategoryId id) {
        if (withProducts.contains(id)) {
            return RemoveCategory.Outcome.HAS_PRODUCTS;
        }
        return categories.delete(id) ? RemoveCategory.Outcome.REMOVED : RemoveCategory.Outcome.NOT_FOUND;
    }

    /** Stands for a product of the category (UC-06), which the catalog's product table would hold. */
    public void addProductTo(CategoryId id) {
        withProducts.add(id);
    }
}
