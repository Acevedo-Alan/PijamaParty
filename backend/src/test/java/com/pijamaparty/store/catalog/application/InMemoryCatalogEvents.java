package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.api.CategoryRenamed;
import com.pijamaparty.store.catalog.api.ProductChanged;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductId;
import java.util.ArrayList;
import java.util.List;

/** Fake of {@link CatalogEvents}: keeps the published events, in order. */
public class InMemoryCatalogEvents implements CatalogEvents {

    private final List<Object> published = new ArrayList<>();

    @Override
    public synchronized void productChanged(ProductId product) {
        published.add(new ProductChanged(product.value()));
    }

    @Override
    public synchronized void categoryRenamed(CategoryId category) {
        published.add(new CategoryRenamed(category.value()));
    }

    public synchronized List<Object> published() {
        return List.copyOf(published);
    }
}
