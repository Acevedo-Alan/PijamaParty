package com.pijamaparty.store.catalog.domain;

/**
 * UC-06 (T-E8-08): the stock filter of the back-office product list, over the available units (on hand minus
 * reserved, UC-07) of the product's active variants. The three never overlap.
 */
public enum ProductStock {
    /** Not {@link #OUT}, and every active variant is above the low-stock threshold. */
    IN_STOCK,
    /** Not {@link #OUT}, and some active variant is at or under the threshold, sold-out ones included. */
    LOW,
    /** No active variant has available units, which includes a product without active variants. */
    OUT
}
