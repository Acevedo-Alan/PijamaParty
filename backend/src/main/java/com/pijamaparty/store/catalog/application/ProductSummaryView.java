package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductId;

/** One row of the back-office product list (UC-06). */
public record ProductSummaryView(ProductId id, CategoryId categoryId, String name, String slug, boolean published,
                                 int variantCount) {
}
