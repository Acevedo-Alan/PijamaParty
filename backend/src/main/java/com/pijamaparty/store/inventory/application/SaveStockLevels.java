package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.StockLevel;

/** Outbound port: writes stock levels within the caller's transaction. */
public interface SaveStockLevels {

    /**
     * Records the level's new movements, oldest first, and changes its stock by each of them. Save a loaded level
     * once. A movement that would leave the stock out of range, or reuses an adjustment key, fails.
     */
    void save(StockLevel level);
}
