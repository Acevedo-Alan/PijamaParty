package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import java.util.Optional;

/** Outbound read port of the filter panel (UC-02), over the same listed products as {@link ProductListingQuery}. */
public interface FilterOptionsQuery {

    /** The options of the listed products of {@code category}, or of the whole shop when empty. */
    FilterOptions options(Optional<CategoryId> category);
}
