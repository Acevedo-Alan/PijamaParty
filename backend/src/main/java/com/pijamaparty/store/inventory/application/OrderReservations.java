package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.OrderRef;
import com.pijamaparty.store.inventory.domain.StockMovement;
import java.util.List;

/** Outbound read port: the reservations of an order (UC-16), within the caller's transaction. */
public interface OrderReservations {

    /** Whether the order reserved anything, settled or not. */
    boolean hasAny(OrderRef order);

    /** Its reservations not yet deducted or released, by variant id. */
    List<StockMovement> outstanding(OrderRef order);
}
