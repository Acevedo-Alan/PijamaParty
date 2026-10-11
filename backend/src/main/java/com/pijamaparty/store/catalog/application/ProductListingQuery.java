package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ProductFilter;
import com.pijamaparty.store.catalog.domain.ProductSort;
import java.util.List;

/**
 * Outbound read port of the public grid (UC-01, UC-02). A product is listed when it is published, its category is
 * active and it has an active priced variant; the stock is the inventory's available stock.
 */
public interface ProductListingQuery {

    /** The listed products that match {@code filter}, in {@code sort} order, skipping {@code offset}. */
    List<ProductListingItem> list(ProductFilter filter, ProductSort sort, int offset, int limit);
}
