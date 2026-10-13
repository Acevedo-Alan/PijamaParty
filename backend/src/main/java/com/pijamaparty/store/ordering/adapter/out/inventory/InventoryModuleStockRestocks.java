package com.pijamaparty.store.ordering.adapter.out.inventory;

import com.pijamaparty.store.inventory.api.StockReservations;
import com.pijamaparty.store.ordering.application.StockRestocks;
import com.pijamaparty.store.ordering.domain.OrderId;
import org.springframework.stereotype.Component;

/** Puts a cancelled order's stock back through the public API of the inventory module, in the caller's transaction. */
@Component
class InventoryModuleStockRestocks implements StockRestocks {

    private final StockReservations reservations;

    InventoryModuleStockRestocks(StockReservations reservations) {
        this.reservations = reservations;
    }

    @Override
    public void restock(OrderId order) {
        reservations.restock(order.value());
    }
}
