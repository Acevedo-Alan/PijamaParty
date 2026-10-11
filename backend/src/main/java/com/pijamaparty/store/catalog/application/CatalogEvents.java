package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductId;

/** Outbound port: publishes the events of {@code catalog.api}; listeners run in the caller's transaction. */
public interface CatalogEvents {

    /** Its name, category, material, description or variants changed. */
    void productChanged(ProductId product);

    void categoryRenamed(CategoryId category);
}
