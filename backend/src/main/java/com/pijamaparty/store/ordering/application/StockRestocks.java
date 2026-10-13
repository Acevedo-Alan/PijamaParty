package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderId;

/** Outbound port: puts back the stock a cancelled paid order took (UC-18), within the caller's transaction. */
public interface StockRestocks {

    /** Every deducted unit of the order goes back on hand; repeating it changes nothing. */
    void restock(OrderId order);
}
