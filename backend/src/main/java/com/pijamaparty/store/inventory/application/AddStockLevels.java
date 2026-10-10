package com.pijamaparty.store.inventory.application;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/** Outbound port: writes stock levels within the caller's transaction. */
public interface AddStockLevels {

    /** Adds a stock level with 0 on hand for each variant that has none; existing ones stay as they are. */
    void addEmpty(Collection<UUID> variantIds, Instant at);
}
