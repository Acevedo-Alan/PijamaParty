package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
import com.pijamaparty.store.catalog.domain.VariantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

class InMemoryProductStoreContractTest extends ProductStoreContractTest<InMemoryProductStore> {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();

    @Override
    protected InMemoryProductStore createSubject() {
        return new InMemoryProductStore(categories);
    }

    @Override
    protected CategoryId givenACategory() {
        UUID id = UUID.randomUUID();
        Category category = Category.create(CategoryId.of(id), new CategoryName("Verano " + id.toString()
            .substring(0, 8)), List.of(), Instant.parse("2026-10-01T12:00:00Z"));
        categories.save(category);
        return category.id();
    }

    @Override
    protected void givenStock(VariantId variant, int onHand, int reserved) {
        subject.setAvailable(variant, onHand - reserved);
    }
}
