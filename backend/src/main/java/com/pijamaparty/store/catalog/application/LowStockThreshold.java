package com.pijamaparty.store.catalog.application;

/** Outbound port (UC-06, T-E8-08): the available units at or under which a variant is low on stock (UC-36). */
public interface LowStockThreshold {

    int units();
}
