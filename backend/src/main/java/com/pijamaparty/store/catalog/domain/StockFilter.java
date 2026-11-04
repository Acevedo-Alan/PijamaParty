package com.pijamaparty.store.catalog.domain;

/**
 * UC-07 (T-E8-09): which active variants the "Stock" screen lists, by their available units (on hand minus reserved).
 */
public enum StockFilter {
    ALL,
    /** Above 0 and at or under the low-stock threshold. */
    LOW,
    /** None available. */
    OUT
}
