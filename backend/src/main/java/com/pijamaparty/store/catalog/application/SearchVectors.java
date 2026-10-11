package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductId;

/** Outbound write port: recomputes the search vectors of products from what is stored, in the caller's transaction. */
public interface SearchVectors {

    void refreshProduct(ProductId product);

    /** Every product of the category, whose name is part of their vectors. */
    void refreshCategory(CategoryId category);
}
