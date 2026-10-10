package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;

/**
 * Outbound port: deletes a category within the caller's transaction. A category with products is not deleted (UC-05),
 * and the caller's transaction stays usable.
 */
public interface RemoveCategory {

    Outcome remove(CategoryId id);

    enum Outcome {
        REMOVED,
        NOT_FOUND,
        HAS_PRODUCTS
    }
}
