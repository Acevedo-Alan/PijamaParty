package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.api.LowStockVariants.LowStockVariant;
import java.util.List;

/**
 * Outbound read port (UC-33): the active variants of published products at or under a threshold of available stock
 * (a variant without stock has none). Within the caller's transaction.
 */
public interface StockWatch {

    /** Sold-out ones first, then by available stock, product name, size and color position. */
    List<LowStockVariant> lowStock(int threshold, int limit);

    long lowStockCount(int threshold);
}
