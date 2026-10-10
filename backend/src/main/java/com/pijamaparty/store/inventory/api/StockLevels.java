package com.pijamaparty.store.inventory.api;

import java.util.Collection;
import java.util.UUID;

/**
 * Opens the stock of new variants (UC-06, UC-07): the catalog calls it in the transaction that creates them, so a
 * variant never exists without its stock level.
 */
public interface StockLevels {

    /**
     * Creates a stock level with nothing on hand for each variant that has none. Joins the caller's transaction, which
     * must exist; opening a variant twice changes nothing.
     */
    void open(Collection<UUID> variantIds);
}
