package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.OrderRef;
import com.pijamaparty.store.inventory.domain.StockMovement;
import java.util.List;

/** Outbound read port: the deductions of an order (UC-18), within the caller's transaction. */
public interface OrderDeductions {

    /** Its deductions whose units were not put back yet, by variant id. */
    List<StockMovement> notRestocked(OrderRef order);
}
