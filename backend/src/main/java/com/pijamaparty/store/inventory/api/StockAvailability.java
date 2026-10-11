package com.pijamaparty.store.inventory.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** The stock other modules may sell (UC-03): available = on hand − reserved. Read only. */
public interface StockAvailability {

    /**
     * The available stock of each variant, with an entry for every one asked for: 0 for a variant without a stock
     * level. Joins the caller's transaction, if any.
     */
    Map<UUID, Integer> available(Collection<UUID> variantIds);
}
