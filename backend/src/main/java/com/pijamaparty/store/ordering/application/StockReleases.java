package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;

/** Outbound port: gives back the stock an order reserved (UC-16), within the caller's transaction. */
public interface StockReleases {

    /** Releases every reservation of the order not yet deducted or released; repeating it changes nothing. */
    void release(OrderId order);
}
