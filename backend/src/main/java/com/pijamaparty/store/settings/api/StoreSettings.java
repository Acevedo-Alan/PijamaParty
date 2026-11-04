package com.pijamaparty.store.settings.api;

/** UC-36, D-E8-03: what the other modules read of the store's settings. Read only; joins the caller's transaction. */
public interface StoreSettings {

    /** The available units at or under which a variant is low on stock, 0 to 1,000 (3 by default). */
    int lowStockThreshold();
}
