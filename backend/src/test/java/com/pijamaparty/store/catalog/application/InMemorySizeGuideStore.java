package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.SizeGuide;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake of {@link LoadSizeGuides}, {@link SaveSizeGuide} and {@link RemoveSizeGuide}, with the database's rules: a guide
 * needs its category and goes with it.
 */
public class InMemorySizeGuideStore implements LoadSizeGuides, SaveSizeGuide, RemoveSizeGuide {

    private final InMemoryCategoryStore categories;
    private final Map<CategoryId, SizeGuide> guides = new ConcurrentHashMap<>();

    public InMemorySizeGuideStore(InMemoryCategoryStore categories) {
        this.categories = categories;
    }

    @Override
    public Optional<SizeGuide> findByCategory(CategoryId categoryId) {
        // A guide of a deleted category is gone with it (V38, on delete cascade).
        return Optional.ofNullable(guides.get(categoryId)).filter(guide -> categories.findById(categoryId).isPresent());
    }

    @Override
    public synchronized SaveSizeGuide.Outcome save(SizeGuide guide) {
        if (categories.findById(guide.categoryId()).isEmpty()) {
            return SaveSizeGuide.Outcome.CATEGORY_NOT_FOUND;
        }
        guides.put(guide.categoryId(), guide);
        return SaveSizeGuide.Outcome.SAVED;
    }

    @Override
    public void remove(CategoryId categoryId) {
        guides.remove(categoryId);
    }
}
