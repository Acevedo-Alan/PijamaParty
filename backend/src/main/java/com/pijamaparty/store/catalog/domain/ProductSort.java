package com.pijamaparty.store.catalog.domain;

/**
 * The orders of the public catalog (UC-02). Prices are the "from" price of each product, and ties go newest first.
 * Adapters map each constant to a fixed SQL expression; the sort never comes from raw request text (RNF-28).
 */
public enum ProductSort {
    NEWEST,
    PRICE_ASC,
    PRICE_DESC
}
