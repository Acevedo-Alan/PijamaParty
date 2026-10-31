package com.pijamaparty.store.backoffice.application;

/**
 * Outbound read port (UC-33, D-E8-03): the available units at or under which a variant is low on stock. The store's
 * settings hold it (UC-36, T-E8-04); until then it is the default.
 */
public interface LowStockThreshold {

    int units();
}
