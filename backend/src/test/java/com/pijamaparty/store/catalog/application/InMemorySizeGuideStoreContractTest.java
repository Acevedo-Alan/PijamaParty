package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
import java.time.Instant;
import java.util.UUID;

class InMemorySizeGuideStoreContractTest extends SizeGuideStoreContractTest<InMemorySizeGuideStore> {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();

    @Override
    protected InMemorySizeGuideStore createSubject() {
        return new InMemorySizeGuideStore(categories);
    }

    @Override
    protected CategoryId givenACategory() {
        var id = CategoryId.of(UUID.randomUUID());
        categories.save(Category.create(id, new CategoryName("Categoría " + id.value()), categories.findAll(),
            Instant.parse("2026-10-01T12:00:00Z")));
        return id;
    }

    @Override
    protected void deleteCategory(CategoryId category) {
        categories.remove(category);
    }
}
