package com.pijamaparty.store.catalog.application;

import java.util.List;

/**
 * Outbound read port of the "Stock" screen (UC-07, T-E8-09): the active variants of every product, published or not,
 * by available units, then product name, size and color in display order. Within the caller's transaction.
 */
public interface StockOverviewPages {

    /** Those the filter lets through, skipping {@code offset} and returning at most {@code limit}. */
    List<StockOverviewRow> page(StockOverviewFilter filter, int offset, int limit);
}
