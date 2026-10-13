package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;

/** Outbound port: the stock a paid order reserved becomes its for good (UC-16), within the caller's transaction. */
public interface StockDeductions {

    /** Deducts every reservation of the order not yet deducted or released; repeating it changes nothing. */
    void deduct(OrderId order);
}
