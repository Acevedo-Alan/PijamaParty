package com.pijamaparty.store.ordering.adapter.out.inventory;

import com.pijamaparty.store.inventory.api.StockReservations;
import com.pijamaparty.store.ordering.application.StockDeductions;
import com.pijamaparty.store.ordering.domain.OrderId;
import org.springframework.stereotype.Component;

/** Deducts a paid order's stock through the public API of the inventory module, in the caller's transaction. */
@Component
class InventoryModuleStockDeductions implements StockDeductions {

    private final StockReservations reservations;

    InventoryModuleStockDeductions(StockReservations reservations) {
        this.reservations = reservations;
    }

    @Override
    public void deduct(OrderId order) {
        reservations.deduct(order.value());
    }
}
